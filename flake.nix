{
  inputs = {
    nixpkgs.url = "github:nixos/nixpkgs/nixos-unstable";

    git-hooks = {
      url = "github:cachix/git-hooks.nix";
      inputs.nixpkgs.follows = "nixpkgs";
    };
  };

  outputs = { self, nixpkgs, git-hooks }: let
    inherit (nixpkgs) lib;

    supportedSystems = [ "x86_64-linux" ];
    forAllSystems = f: nixpkgs.lib.genAttrs
      supportedSystems (system: f nixpkgs.legacyPackages.${system});
  in {
    formatter = forAllSystems (pkgs: pkgs.alejandra);

    checks = forAllSystems (pkgs: {
      pre-commit-check = git-hooks.lib.${pkgs.system}.run {
        src = ./.;
        hooks = lib.genAttrs [
            "convco"
            "trim-trailing-whitespace"
            "deadnix"
            "alejandra"
          ] (_: {enable = true;});
      };
    });

    devShells = forAllSystems (pkgs: let
      py-env = pkgs.python313.withPackages (p: with p; [ pytest tomli ]);
    in {
      default = pkgs.mkShell {
        packages = with pkgs; [
          docker
          docker-compose

          minikube
          kubectl
          py-env
        ];
      };
    });

    packages = forAllSystems (pkgs: {
      ubuntu-test-vm = pkgs.callPackage ./ubuntu-test-vm.nix { };
    });
  };
}
