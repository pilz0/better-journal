{
  inputs.gradle2nix.url = "github:tadfisher/gradle2nix/v2";

  outputs =
    { self, gradle2nix }:
    {
      packages.x86_64-linux.default = gradle2nix.builders.x86_64-linux.buildGradlePackage {
        pname = "app";
        version = "11.11";
        lockFile = ./gradle.lock;
      };
    };
}
