{ lib }:
let conf = {
  system = "x86_64-linux";
  modules = [
    {
      system.stateVersion = "25.05";
      fileSystems."/" = {
        device = "nodev";
      };
     }
    ({ pkgs, ... }: {
      services.openssh = {
        enable = true;
        settings.PasswordAuthentication = false;
      };

      users.users.root.openssh.authorizedKeys.keys = [
        "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIIWJy57pUiBdGw0bHqfaIJOtvvhM2N3AF746/dbGvQY4 edyjox@gmail.com"
      ];

      environment.systemPackages = with pkgs; [
        python3
        jdk11
      ];
    })
  ];
};
in (lib.nixosSystem conf) // { inherit conf; }
