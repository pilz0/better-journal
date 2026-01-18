{
  description = "Better Journal - A journaling application";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
    gradle2nix = {
      url = "github:tadfisher/gradle2nix/v2";
      inputs.nixpkgs.follows = "nixpkgs";
    };
  };

  outputs =
    {
      self,
      nixpkgs,
      flake-utils,
      gradle2nix,
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

        androidSdk = pkgs.androidenv.composeAndroidPackages {
          cmdLineToolsVersion = "9.0"; # Matches your previous intent
          buildToolsVersions = [ "35.0.0" "34.0.0" ];
          platformVersions = [ "36" "35" "34" "33" "31" ];
          abiVersions = [ "x86_64" ];
          includeEmulator = false;
        };
      in
      {
        packages = {
          default = self.packages.${system}.apk;
          apk = gradle2nix.builders.${system}.buildGradlePackage {
            pname = "freaklog apk";
            version = "11.13";
            lockFile = ./gradle.lock;
            src = ./.;
            gradleBuildFlags = [ "assembleRelease" ];
            ANDROID_HOME = "${androidSdk.androidsdk}/libexec/android-sdk";
            preBuild = ''
              rm -f local.properties
              export ANDROID_USER_HOME=$(mktemp -d)
              mkdir -p $ANDROID_USER_HOME/.android
              export GRADLE_OPTS="-Djava.io.tmpdir=$ANDROID_USER_HOME/tmp"
              mkdir -p $ANDROID_USER_HOME/tmp
            '';
            nativeBuildInputs = [
              pkgs.jre17_minimal
              androidSdk.androidsdk
            ];
            overrides = {
               "com.android.tools.build:aapt2:8.13.2-14304508" = {
                 "aapt2-8.13.2-14304508-linux.jar" = src: pkgs.runCommandCC src.name {
                  nativeBuildInputs = [ pkgs.jdk pkgs.libgcc pkgs.autoPatchelfHook ];
                  dontAutoPatchelf = true;
                } ''
                  cp ${src} aapt2.jar
                  jar xf aapt2.jar aapt2
                  chmod +x aapt2
                  autoPatchelf aapt2
                  jar uf aapt2.jar aapt2
                  cp aapt2.jar $out
                '';
              };
            };
            installPhase = ''
              mkdir -p $out/bin
              cp app/build/outputs/apk/release/*.apk $out/bin/
            '';
          };
           aab = gradle2nix.builders.${system}.buildGradlePackage {
             pname = "freaklog aab";
             version = "11.13";
             lockFile = ./gradle.lock;
             src = ./.;
             gradleBuildFlags = [ "bundle" ];
             ANDROID_HOME = "${androidSdk.androidsdk}/libexec/android-sdk";
             preBuild = ''
               rm -f local.properties
               export ANDROID_USER_HOME=$(mktemp -d)
               mkdir -p $ANDROID_USER_HOME/.android
               export GRADLE_OPTS="-Djava.io.tmpdir=$ANDROID_USER_HOME/tmp"
               mkdir -p $ANDROID_USER_HOME/tmp
             '';
             nativeBuildInputs = [
               pkgs.jre17_minimal
               androidSdk.androidsdk
             ];
             overrides = {
               "com.android.tools.build:aapt2:8.13.2-14304508" = {
                 "aapt2-8.13.2-14304508-linux.jar" = src: pkgs.runCommandCC src.name {
                   nativeBuildInputs = [ pkgs.jdk pkgs.libgcc pkgs.autoPatchelfHook ];
                   dontAutoPatchelf = true;
                 } ''
                   cp ${src} aapt2.jar
                   jar xf aapt2.jar aapt2
                   chmod +x aapt2
                   autoPatchelf aapt2
                   jar uf aapt2.jar aapt2
                   cp aapt2.jar $out
                 '';
               };
             };
             installPhase = ''
               mkdir -p $out/bin
               cp app/build/outputs/bundle/release/*.aab $out/bin/
               cp app/build/outputs/bundle/debug/*.aab $out/bin/
             '';
           };

        };

        devShells.default = pkgs.mkShell {
          packages = with pkgs; [
            jetbrains.jdk
            androidSdk.androidsdk
          ];
        };
        formatter = pkgs.nixpkgs-fmt;
      }
    );
}