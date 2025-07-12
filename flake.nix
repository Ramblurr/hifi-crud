{
  description = "hifi development environment";
  
  inputs = {
    nixpkgs.url = "https://flakehub.com/f/NixOS/nixpkgs/0.1"; # tracks nixpkgs unstable branch
    datomic-pro.url = "https://flakehub.com/f/Ramblurr/datomic-pro/0.7.0";
    flake-utils.url = "github:numtide/flake-utils";
    treefmt-nix.url = "github:numtide/treefmt-nix";
  };
  
  outputs =
    {
      self,
      nixpkgs,
      datomic-pro,
      flake-utils,
      treefmt-nix,
    }:
    let
      javaVersion = 21;
    in
    {
      overlays.default =
        final: prev:
        let
          jdk = prev."jdk${toString javaVersion}";
        in
        {
          clojure = prev.clojure.override { inherit jdk; };
          datomic-pro = prev.datomic-pro.override {
            extraJavaPkgs = [
              prev.sqlite-jdbc
            ];
          };
        };
    }
    // flake-utils.lib.eachDefaultSystem (
      system:
      let
        pkgs = import nixpkgs {
          inherit system;
          overlays = [
            datomic-pro.overlays.${system}
            self.overlays.default
          ];
        };
        treefmtEval = treefmt-nix.lib.evalModule pkgs ./treefmt.nix;
      in
      {
        formatter = treefmtEval.config.build.wrapper;
        
        devShells.default = pkgs.mkShell {
          DATOMIC_PRO_PEER_JAR = "${pkgs.datomic-pro-peer}/share/java/datomic-pro-peer-1.0.7387.jar";
          packages = with pkgs; [
            gum
            bun
            clojure
            clojure-lsp
            babashka
            clj-kondo
            datomic-pro
            datomic-pro-peer
          ];
        };
        
        checks = {
          formatting = treefmtEval.config.build.check self;
        };
      }
    );
}