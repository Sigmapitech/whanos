{
  stdenv,
  lib,
  fetchurl,
  cloud-utils,
  qemu,
}:
stdenv.mkDerivation {
  name = "ubuntu-test-vm.nix";

  src = fetchurl {
    url = "https://cloud-images.ubuntu.com/noble/20251001/noble-server-cloudimg-amd64.img";
    hash = "sha256-AtwYbcSRUUJU31jfKaWHfJPKkKx5DMkaFk6pDtOguwU=";
  };

  dontUnpack = true;

  buildInputs = [ cloud-utils qemu ];

  installPhase = ''
    mkdir -p $out

    echo "#cloud-config
password: asdfqwer
chpasswd: { expire: False }
ssh_pwauth: True
growpart:
  mode: auto
  devices: ['/']
resize_rootfs: true

" > user-data

    cloud-localds $out/user-data.img user-data
    qemu-img create \
      -f qcow2 -F qcow2 \
      -b $src $out/noble-server-cloudimg-amd64-expanded.qcow2 10G

    cp $src $out/noble-server-cloudimg-amd64.img

    cat > $out/run-vm.sh <<'EOF'
#!/usr/bin/env bash
set -euxo pipefail

IMG_DIR="$(dirname "$0")"

exec ${lib.getExe qemu} \
  -cpu max -name ubuntu-test-vm -m 2048 -smp 2 \
  -drive file="$IMG_DIR/noble-server-cloudimg-amd64-expanded.qcow2",format=qcow2,if=virtio \
  -drive file="$IMG_DIR/user-data.img",format=raw,if=virtio \
  -netdev user,id=net0,hostfwd=tcp::2222-:22 \
  -device virtio-net-pci,netdev=net0 \
  -snapshot
EOF

    chmod +x $out/run-vm.sh
  '';

  meta = {
    description = "Ubuntu Noble VM";
    license = lib.licenses.mit;
    platforms = lib.platforms.linux;
    maintainers = with lib.maintainers; [ sigmanificient ];
  };
}
