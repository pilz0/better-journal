{ inputs, pkgs, ... }:

let
  # Configure which Android tools we'll need (mostly the recommended ones)
  sdk = (import inputs.android-nixpkgs { }).sdk (sdkPkgs: with sdkPkgs; [
    patcher-v4
    platform-tools
    system-images-android-32-google-apis-x86-64
  cmdline-tools-latest
  build-tools-34-0-0
  platforms-android-34
  emulator
  android-sdk
  ]);
in
{
  env.GREET = "devenv";
  android.enable = true;
  android.android-studio.enable = false;
  # Install various packages from Nix that we'll need for this project
  packages = with pkgs; [
    neofetch
  ];

  # Ensure our path has various Android SDK things in it
 # enterShell = ''
 #   export PATH="${sdk}/bin:$PATH"
 #   ${(builtins.readFile "${sdk}/nix-support/setup-hook")}
 # '';

  # Create the initial AVD that's needed by the emulator
  scripts.create-avd.exec = "avdmanager create avd --force --name phone --package 'system-images;android-332;google_apis;x86_64'";

  # These processes will all run whenever we run `devenv run`
  processes.emulator.exec = "emulator -avd phone -skin 720x1280";
}