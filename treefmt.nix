{
  projectRootFile = "flake.nix";
  programs = {
    deadnix.enable = true;
    statix.enable = true;
    nixfmt = {
      enable = true;
      strict = true;
    };
    shellcheck.enable = true;
    cljfmt.enable = true;
  };
  settings = {
    global.excludes = [ ".envrc" ];
    formatter = { };
  };
}
