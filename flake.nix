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
  };

  outputs =
    { self
    , nixpkgs
    , flake-utils
    , gradle2nix
    ,
    }:
    flake-utils.lib.eachDefaultSystem
      (
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
            platformVersions = [ "37" "36" ];
            abiVersions = [ "x86_64" ];
            includeEmulator = false;
          };

          androidHome = "${androidSdk.androidsdk}/libexec/android-sdk";

          # Single source of truth for the version is app/build.gradle.kts.
          version = builtins.head (
            builtins.match ".*versionName = \"([^\"]+)\".*" (builtins.readFile ./app/build.gradle.kts)
          );

          # Builds one unsigned release artifact. `artifact` is the path Gradle
          # writes, `name` is the file name it gets under $out/bin.
          mkFreaklog =
            { pname
            , task
            , artifact
            , name
            ,
            }:
            gradle2nix.builders.${system}.buildGradlePackage {
              inherit pname version;
              # AGP 9 needs Gradle 9; nixpkgs' default `gradle` is still the 8.x line.
              gradle = pkgs.gradle_9;
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

          checks = {
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
          };

          devShells.default = pkgs.mkShell {
            packages = with pkgs; [
              pkgs.jdk17
              androidSdk.androidsdk
            ];
            shellHook = ''
              export JAVA_HOME=${pkgs.jdk17.home}
            '';
          };
          formatter = pkgs.nixpkgs-fmt;
        }
      )
    // {
      # The Android SDK is only packaged for x86_64-linux among Linux systems.
      hydraJobs = {
        apk.x86_64-linux = self.packages.x86_64-linux.apk;
        aab.x86_64-linux = self.packages.x86_64-linux.aab;
        nix-version-sync.x86_64-linux = self.checks.x86_64-linux.nix-version-sync;
        nix-gradle-lock-present.x86_64-linux = self.checks.x86_64-linux.nix-gradle-lock-present;
        nix-release-artifact-paths.x86_64-linux = self.checks.x86_64-linux.nix-release-artifact-paths;
      };
    };
}
