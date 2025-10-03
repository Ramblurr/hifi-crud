{
  description = "hifi development environment";

  inputs = {
    nixpkgs.url = "https://flakehub.com/f/NixOS/nixpkgs/0.1"; # tracks nixpkgs unstable branch
    datomic-pro.url = "https://flakehub.com/f/outskirtslabs/datomic-pro/0.8.0";
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
    {
      overlays.default =
        _final: prev:
        let
          #jdk = prev."jdk${toString javaVersion}";
          jdk = prev.graalvmPackages.graalvm-ce;
        in
        {
          clojure = prev.clojure.override { jdk21 = jdk; };
          datomic-pro = prev.datomic-pro.override { extraJavaPkgs = [ prev.sqlite-jdbc ]; };
          inherit jdk;
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
          DATOMIC_PRO_PEER_JAR = "${pkgs.datomic-pro-peer_1_0_7394}/share/java/datomic-pro-peer-1.0.7394.jar";
          packages = with pkgs; [
            jdk
            gum
            bun
            clojure
            clojure-lsp
            babashka
            clj-kondo
            datomic-pro_1_0_7394
          ];
        };

        checks = {
          formatting = treefmtEval.config.build.check self;
        };
      }
    );
}
