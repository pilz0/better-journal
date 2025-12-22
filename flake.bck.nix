{
  description = "A development shell for android";
  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-unstable";
  };
  outputs =
    inputs@{
      flake-parts,
      self,
      ...
    }:
    # https://flake.parts/
    flake-parts.lib.mkFlake { inherit inputs; } {
      systems = [
        "x86_64-linux"
        "aarch64-darwin"
        "x86_64-darwin"
      ];
      perSystem =
        {
          pkgs,
          inputs',
          lib,
          system,
          ...
        }:
        let
          platformVersion = "35";
          systemImageType = "default";
          androidEnv = pkgs.androidenv.override { licenseAccepted = true; };
          androidComp = (
            androidEnv.composeAndroidPackages {
              cmdLineToolsVersion = "8.0";
              includeNDK = false;
              # we need some platforms
              platformVersions = [
                "35"
                platformVersion
              ];
              # we need an emulator
              includeEmulator = false;
              includeSystemImages = false;
              systemImageTypes = [
                systemImageType
                # "google_apis"
              ];
              abiVersions = [
                "x86"
                "x86_64"
                "armeabi-v7a"
                "arm64-v8a"
              ];
              cmakeVersions = [ "3.10.2" ];
            }
          );
          android-sdk = (pkgs.android-studio.withSdk androidComp.androidsdk);
        in
        {
          _module.args.pkgs = import self.inputs.nixpkgs {
            inherit system;
            config.allowUnfree = true;
            config.android_sdk.accept_license = true;
            config.allowUnfreePredicate =
              pkg:
              builtins.elem (lib.getName pkg) [
                "terraform"
              ];
          };
          packages.android-emulator = androidEnv.emulateApp {
            name = "emulate-MyAndroidApp";
            platformVersion = platformVersion;
            abiVersion = "x86_64"; # armeabi-v7a, mips, x86_64, arm64-v8a
            systemImageType = systemImageType;
          };
          devShells.default = pkgs.mkShell {
            name = "dev";

            # Available packages on https://search.nixos.org/packages
            buildInputs = with pkgs; [
              just
              gradle_9
              jdk
              android-sdk
            ];

            shellHook = ''
              echo "Welcome to the android devshell!"
            '';

            ANDROID_HOME = "${androidComp.androidsdk}/libexec/android-sdk";
            ANDROID_SDK_ROOT = "${androidComp.androidsdk}/libexec/android-sdk";
            ANDROID_NDK_ROOT = "${androidComp.androidsdk}/libexec/android-sdk/ndk-bundle";

            LD_LIBRARY_PATH = "$LD_LIBRARY_PATH:${
              with pkgs;
              lib.makeLibraryPath [
                wayland
                libxkbcommon
                fontconfig
              ]
            }";
          };
        };
    };
}
