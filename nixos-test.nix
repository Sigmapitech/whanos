{ nixpkgs, pkgs, nixos-system }:
let
  vm-config = { config, lib, ... }: {
     imports = [
       (nixpkgs + "/nixos/tests/common/user-account.nix")
       (nixpkgs + "/nixos/tests/common/auto.nix")
     ];

    virtualisation.forwardPorts = [
      { from = "host"; host.port = 2222; guest.port = 22; }
    ];
  };
in pkgs.testers.runNixOSTest {
  name = "nixos-test-vm";

  node = {
    inherit pkgs;

    specialArgs = nixos-system.conf.specialArgs or { };
    pkgsReadOnly = false;
  };

  nodes = {
    machine.imports =
      nixos-system.conf.modules
      ++ [ vm-config ];
  };

  testScript = ''
    start_all()

    # Keep VM running indefinitely
    machine.wait_for_file("plop")
  '';
}
