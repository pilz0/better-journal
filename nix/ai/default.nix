# Single source of truth for AI agent instructions. Each entry in `files` is
# rendered from the Markdown fragments next to this file and committed at that
# path, because agents read them straight from the checkout.
{ pkgs }:
let
  banner = "<!-- Generated from nix/ai/ by `nix run .#ai-sync`. Do not edit by hand. -->\n\n";
  quickstart = builtins.readFile ./quickstart.md;
  reference = builtins.readFile ./reference.md;

  files = {
    "AGENTS.md" = banner + ''
      # AI Agent Guide — Freaklog Android

      The first sections are the quickstart. Everything from "Repository overview" on is the deep reference.

    '' + quickstart + "\n" + reference;

    ".github/copilot-instructions.md" = banner + ''
      # Copilot instructions for `pilz0/better-journal`

      Read this, then use `AGENTS.md` as the deep reference.

    '' + quickstart;
  };

  tree = pkgs.linkFarm "ai-instructions" (
    pkgs.lib.mapAttrsToList (path: text: { name = path; path = pkgs.writeText (baseNameOf path) text; }) files
  );

  paths = builtins.attrNames files;
in
{
  inherit tree;

  # Writes the generated files into the repository it is run from.
  sync = pkgs.writeShellApplication {
    name = "ai-sync";
    runtimeInputs = [ pkgs.git pkgs.coreutils ];
    text = ''
      root="$(git rev-parse --show-toplevel)"
      for p in ${pkgs.lib.escapeShellArgs paths}; do
        install -Dm644 "${tree}/$p" "$root/$p"
        echo "wrote $p"
      done
    '';
  };

  # Fails when a committed file differs from what the sources render to.
  check = src: pkgs.runCommand "ai-instructions-in-sync" { } ''
    set -eu
    for p in ${pkgs.lib.escapeShellArgs paths}; do
      ${pkgs.diffutils}/bin/diff -u "${src}/$p" "${tree}/$p" || {
        echo "$p is out of date. Run: nix run .#ai-sync"
        exit 1
      }
    done
    touch $out
  '';
}
