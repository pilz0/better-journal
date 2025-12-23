{
  inputs.flake-utils.url = "github:numtide/flake-utils";
  inputs.gradle2nix.url = "github:tadfisher/gradle2nix/v2";
  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";

  outputs =
    {
      self,
      nixpkgs,
      gradle2nix,
      flake-utils,
    }:
    {
      name = "freaklog";

      devShells.x86_64-linux.default =
        let
          pkgs = nixpkgs.legacyPackages.x86_64-linux;
        in
        pkgs.mkShell {
          #          JAVA_HOME = "/nix/store/mibyr0q42jihn6i0xrda79bljkfyrhfs-android-studio-stable-2025.2.1.8-unwrapped/jbr";
          packages = with pkgs; [
            git
            jetbrains.jdk
          ];
        };

      app = gradle2nix.builders.x86_64-linux.buildGradlePackage {
        pname = "app";
        version = "11.12";
        lockFile = ./gradle.lock;
      };
    };
}
