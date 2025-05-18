{
  description = "hifi development environment";
  inputs = {
    nixpkgs.url = "https://flakehub.com/f/NixOS/nixpkgs/0.1"; # tracks nixpkgs unstable branch
    datomic-pro.url = "https://flakehub.com/f/Ramblurr/datomic-pro/0.6.1";
  };
  outputs =
    inputs:
    let
      javaVersion = 21;
      supportedSystems = [
        "x86_64-linux"
        "aarch64-linux"
        "x86_64-darwin"
        "aarch64-darwin"
      ];
      forEachSupportedSystem =
        f:
        inputs.nixpkgs.lib.genAttrs supportedSystems (
          system:
          f {
            pkgs = import inputs.nixpkgs {
              inherit system;
              overlays = [
                inputs.datomic-pro.overlays.${system}
                inputs.self.overlays.default
              ];
            };
          }
        );
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

      devShells = forEachSupportedSystem (
        { pkgs }:
        {
          default = pkgs.mkShell {
            DATOMIC_PRO_PEER_JAR = "${pkgs.datomic-pro-peer}/share/java/datomic-pro-peer-1.0.7364.jar";
            packages = with pkgs; [
              clojure
              clojure-lsp
              babashka
              clj-kondo
              datomic-pro
              datomic-pro-peer
            ];
          };
        }
      );
    };
}
