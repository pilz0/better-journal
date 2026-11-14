{
  description = "Better Journal - A journaling application";
  nixConfig = {
    extra-allowed-uris = [
      "http://"
      "https://"
    ];
  };
  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
    gradle2nix = {
      url = "github:tadfisher/gradle2nix/v2";
      inputs.nixpkgs.follows = "nixpkgs";
    };
    treefmt-nix = {
      url = "github:numtide/treefmt-nix";
      inputs.nixpkgs.follows = "nixpkgs";
    };
  };

  outputs =
    {
      self,
      nixpkgs,
      flake-utils,
      gradle2nix,
      treefmt-nix,
    }:
    flake-utils.lib.eachDefaultSystem (
      system:
      let
        pkgs = import nixpkgs {
          inherit system;
          config = {
            allowUnfree = true;
            allowBroken = true;
            android_sdk.accept_license = true;
          };
        };

        buildToolsVersion = "36.0.0";

        androidSdk = pkgs.androidenv.composeAndroidPackages {
          cmdLineToolsVersion = "9.0"; # Matches your previous intent
          buildToolsVersions = [ buildToolsVersion ];
          platformVersions = [
            "37"
            "36"
          ];
          abiVersions = [ "x86_64" ];
          includeEmulator = false;
        };

        androidHome = "${androidSdk.androidsdk}/libexec/android-sdk";

        # Keep the offline Nix build on exactly the wrapper's Gradle distribution.
        wrapperProperties = builtins.readFile ./gradle/wrapper/gradle-wrapper.properties;
        gradleVersion = builtins.head (builtins.match ".*gradle-([0-9.]+)-bin[.]zip.*" wrapperProperties);
        gradleHash = builtins.head (
          builtins.match ".*distributionSha256Sum=([0-9a-f]+).*" wrapperProperties
        );
        gradle =
          (pkgs.gradle-packages.mkGradle {
            version = gradleVersion;
            hash = "sha256:${gradleHash}";
            defaultJava = pkgs.jdk17;
          }).wrapped;

        ai = import ./nix/ai { inherit pkgs; };

        treefmtEval = treefmt-nix.lib.evalModule pkgs {
          projectRootFile = "flake.nix";
          programs = {
            # Nix files
            nixfmt.enable = true;
            # Kotlin / Kotlin Script files
            ktfmt.enable = true;
            # YAML, JSON, Markdown, TOML
            prettier = {
              enable = true;
              # Don't touch generated / data files that must stay as-is.
              excludes = [
                "app/src/main/assets/Substances.json"
                "app/schemas/.*"
                "gradle.lock"
                ".*\\.lock"
              ];
            };
          };
        };

        # Single source of truth for the version is app/build.gradle.kts.
        version = builtins.head (
          builtins.match ".*versionName = \"([^\"]+)\".*" (builtins.readFile ./app/build.gradle.kts)
        );

        # Builds one unsigned release artifact. `artifact` is the path Gradle
        # writes, `name` is the file name it gets under $out/bin.
        mkFreaklog =
          {
            pname,
            task,
            artifact,
            name,
          }:
          gradle2nix.builders.${system}.buildGradlePackage {
            inherit pname version;
            inherit gradle;
            lockFile = ./gradle.lock;
            src = ./.;
            gradleBuildFlags = [
              task
              # AGP otherwise downloads a prebuilt aapt2 from Maven, which is a
              # per-OS artifact (so the lock would only work on the OS it was
              # generated on) and is not runnable on NixOS. The SDK's copy is
              # already patched by nixpkgs.
              "-Pandroid.aapt2FromMavenOverride=${androidHome}/build-tools/${buildToolsVersion}/aapt2"
              # `jvmToolchain(17)` must resolve to this JDK, not to whatever the
              # host happens to have installed (or a download attempt).
              "-Porg.gradle.java.installations.paths=${pkgs.jdk17.home}"
              "-Porg.gradle.java.installations.auto-detect=false"
              "-Porg.gradle.java.installations.auto-download=false"
            ];
            ANDROID_HOME = androidHome;
            preBuild = ''
              rm -f local.properties
              export ANDROID_USER_HOME=$(mktemp -d)
              mkdir -p $ANDROID_USER_HOME/.android
              export GRADLE_OPTS="-Djava.io.tmpdir=$ANDROID_USER_HOME/tmp"
              mkdir -p $ANDROID_USER_HOME/tmp
            '';
            nativeBuildInputs = [
              pkgs.jdk17
              androidSdk.androidsdk
            ];
            installPhase = ''
              mkdir -p $out/bin $out/nix-support
              cp ${artifact} $out/bin/${name}
              echo "file binary-dist $out/bin/${name}" > $out/nix-support/hydra-build-products
            '';
          };
      in
      {
        packages = {
          default = self.packages.${system}.apk;
          apk = mkFreaklog {
            pname = "freaklog-apk-release";
            task = "assembleRelease";
            artifact = "app/build/outputs/apk/release/app-release-unsigned.apk";
            name = "app-release-unsigned.apk";
          };
          aab = mkFreaklog {
            pname = "freaklog-aab-release";
            task = "bundleRelease";
            artifact = "app/build/outputs/bundle/release/app-release.aab";
            name = "app-release-unsigned.aab";
          };
        };

        apps.ai-sync = {
          type = "app";
          program = pkgs.lib.getExe ai.sync;
        };

        checks = {
          treefmt = treefmtEval.config.build.check self;

          ai-instructions-in-sync = ai.check self;

          workflow-lint =
            pkgs.runCommand "workflow-lint"
              {
                nativeBuildInputs = [
                  pkgs.actionlint
                  pkgs.shellcheck
                ];
              }
              ''
                actionlint -shellcheck=shellcheck -pyflakes= ${./.github/workflows}/*.yml
                touch $out
              '';

          nix-version-sync = pkgs.runCommand "nix-version-sync" { } ''
            set -eu
            gradle_version="$(${pkgs.gnugrep}/bin/grep -E 'versionName = "[^"]+"' ${./app/build.gradle.kts} | ${pkgs.coreutils}/bin/head -n1 | ${pkgs.gnused}/bin/sed -E 's/.*versionName = "([^"]+)".*/\1/')"
            if [ -z "$gradle_version" ]; then
              echo "Failed to parse versionName from app/build.gradle.kts"
              exit 1
            fi
            if [ "$gradle_version" != "${version}" ]; then
              echo "Version mismatch: Gradle=$gradle_version Nix=${version}"
              exit 1
            fi
            ${pkgs.coreutils}/bin/touch $out
          '';

          nix-gradle-lock-present = pkgs.runCommand "nix-gradle-lock-present" { } ''
            set -eu
            if [ ! -s ${./gradle.lock} ]; then
              echo "gradle.lock is missing or empty"
              exit 1
            fi
            ${pkgs.coreutils}/bin/touch $out
          '';

          nix-release-artifact-paths = pkgs.runCommand "nix-release-artifact-paths" { } ''
            set -eu
            flake_file=${./flake.nix}
            ${pkgs.gnugrep}/bin/grep -q 'app/build/outputs/apk/release/app-release-unsigned.apk' "$flake_file"
            ${pkgs.gnugrep}/bin/grep -q 'app/build/outputs/bundle/release/app-release.aab' "$flake_file"
            ${pkgs.coreutils}/bin/touch $out
          '';

          nix-substances-json-valid = pkgs.runCommand "nix-substances-json-valid" { } ''
            set -eu
            if [ ! -s ${./app/src/main/assets/Substances.json} ]; then
              echo "Substances.json is missing or empty"
              exit 1
            fi
            substance_count="$(${pkgs.jq}/bin/jq '.substances | length' ${./app/src/main/assets/Substances.json})"
            if [ "$substance_count" -le 0 ]; then
              echo "Substances.json has no substances"
              exit 1
            fi
            ${pkgs.coreutils}/bin/touch $out
          '';

          nix-room-schema-current-present = pkgs.runCommand "nix-room-schema-current-present" { } ''
            set -eu
            schema_file="${./app/schemas/foo.pilz.freaklog.data.room.AppDatabase/30.json}"
            if [ ! -s "$schema_file" ]; then
              echo "Room schema version 30 (current) is missing or empty"
              exit 1
            fi
            version="$(${pkgs.jq}/bin/jq '.formatVersion' "$schema_file")"
            if [ "$version" != "1" ]; then
              echo "Unexpected Room schema formatVersion: $version"
              exit 1
            fi
            # Verify the latest schema file covers all expected tables.
            table_count="$(${pkgs.jq}/bin/jq '.tableHashes | keys | length' "$schema_file")"
            if [ "$table_count" -lt 25 ]; then
              echo "Room schema v30 has only $table_count tables, expected at least 25"
              exit 1
            fi
            ${pkgs.coreutils}/bin/touch $out
          '';

          nix-no-legacy-package-leaks = pkgs.runCommand "nix-no-legacy-package-leaks" { } ''
            set -eu
            if ${pkgs.gnugrep}/bin/grep -rn "com\.isaakhanimann\.journal" ${./app/src/main} ; then
              echo "Found legacy package references to com.isaakhanimann.journal in app/src/main"
              exit 1
            fi
            ${pkgs.coreutils}/bin/touch $out
          '';

          nix-workflow-security-invariants = pkgs.runCommand "nix-workflow-security-invariants" { } ''
            set -eu
            for wf in ${./.github/workflows}/*.yml; do
              ${pkgs.gnugrep}/bin/grep -q 'permissions: {}' "$wf" || {
                echo "Workflow $wf missing deny-all permissions: {}"
                exit 1
              }
              ${pkgs.gnugrep}/bin/grep -q 'step-security/harden-runner' "$wf" || {
                echo "Workflow $wf missing step-security/harden-runner"
                exit 1
              }
            done
            ${pkgs.coreutils}/bin/touch $out
          '';
        };

        # Also the environment for AI agents: Copilot's cloud agent loads it
        # in .github/workflows/copilot-setup-steps.yml.
        devShells.default = pkgs.mkShell {
          packages = [
            pkgs.jdk17
            pkgs.jdk21
            pkgs.actionlint
            pkgs.shellcheck
            androidSdk.androidsdk
            pkgs.ripgrep
            pkgs.jq
            ai.sync
            treefmtEval.config.build.wrapper
          ];
          JAVA_HOME = pkgs.jdk17.home;
          JDK17_HOME = pkgs.jdk17.home;
          JDK21_HOME = pkgs.jdk21.home;
          ANDROID_HOME = androidHome;
          ANDROID_SDK_ROOT = androidHome;
          # Same deterministic defaults as CI.
          TZ = "UTC";
          LANG = "en_US.UTF-8";
          shellHook = ''
            if root="$(git rev-parse --show-toplevel 2>/dev/null)"; then
              for p in AGENTS.md .github/copilot-instructions.md; do
                cmp -s "${ai.tree}/$p" "$root/$p" || echo "warning: $p is out of date with nix/ai/, run: ai-sync" >&2
              done
            fi
          '';
        };
        formatter = treefmtEval.config.build.wrapper;
      }
    )
    // {
      # The Android SDK is only packaged for x86_64-linux among Linux systems.
      hydraJobs = {
        apk.x86_64-linux = self.packages.x86_64-linux.apk;
        aab.x86_64-linux = self.packages.x86_64-linux.aab;
        treefmt.x86_64-linux = self.checks.x86_64-linux.treefmt;
        ai-instructions-in-sync.x86_64-linux = self.checks.x86_64-linux.ai-instructions-in-sync;
        workflow-lint.x86_64-linux = self.checks.x86_64-linux.workflow-lint;
        nix-version-sync.x86_64-linux = self.checks.x86_64-linux.nix-version-sync;
        nix-gradle-lock-present.x86_64-linux = self.checks.x86_64-linux.nix-gradle-lock-present;
        nix-release-artifact-paths.x86_64-linux = self.checks.x86_64-linux.nix-release-artifact-paths;
        nix-substances-json-valid.x86_64-linux = self.checks.x86_64-linux.nix-substances-json-valid;
        nix-room-schema-current-present.x86_64-linux = self.checks.x86_64-linux.nix-room-schema-current-present;
        nix-no-legacy-package-leaks.x86_64-linux = self.checks.x86_64-linux.nix-no-legacy-package-leaks;
        nix-workflow-security-invariants.x86_64-linux =
          self.checks.x86_64-linux.nix-workflow-security-invariants;
      };
    };
}
