{
  description = "Better Journal - A journaling application";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
    gradle2nix = {
      url = "github:tadfisher/gradle2nix/v2";
      inputs.nixpkgs.follows = "nixpkgs"; # Reduce duplication
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
        pkgs = nixpkgs.legacyPackages.${system};
      in
      {
        packages = {
          default = self.packages.${system}.app;
          app = gradle2nix.builders.${system}.buildGradlePackage {
            pname = "app";
            version = "11.12";
            lockFile = ./gradle.lock;
            src = ./.;
          };
        };

        devShells.default = pkgs.mkShell {
          packages = with pkgs; [
            jetbrains.jdk
          ];
        };

        formatter = pkgs.nixpkgs-fmt;
      }
    );
}
