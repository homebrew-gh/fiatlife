# StartOS 0.4.x package (TypeScript SDK + start-cli).
#
#   make x86-import   # build image + pack → fiatlife_x86_64.s9pk
#   make x86-pack       # pack only (image must exist)
#
# Docs: https://docs.start9.com/packaging/0.4.0.x/

ARCHES := x86 arm

.PHONY: verify image-import clean-rootfs x86-import arm-import x86-pack arm-pack

x86-import: check-deps image-import
	@chmod +x scripts/pack-local-image.sh
	@scripts/pack-local-image.sh x86_64

arm-import: check-deps image-import
	@chmod +x scripts/pack-local-image.sh
	@scripts/pack-local-image.sh aarch64

x86-pack: check-deps
	@chmod +x scripts/pack-local-image.sh
	@scripts/pack-local-image.sh x86_64

arm-pack: check-deps
	@chmod +x scripts/pack-local-image.sh
	@scripts/pack-local-image.sh aarch64

image-import: check-deps
	@chmod +x scripts/build-image-import.sh scripts/docker.sh
	@scripts/build-image-import.sh "start9/fiatlife/main:0.1.0"
	@echo "Local Docker image ready."

verify: check-deps
	@test -f fiatlife_x86_64.s9pk || (echo "Error: fiatlife_x86_64.s9pk not found. Run: make x86-import" && exit 1)
	start-cli s9pk inspect fiatlife_x86_64.s9pk manifest >/dev/null
	@echo "OK: fiatlife_x86_64.s9pk"

clean-rootfs:
	@sudo rm -rf ../../apps/web/.pack/rootfs

include s9pk.mk

$(BASE_NAME).s9pk: $(INGREDIENTS) $(GIT_DEPS)
	@$(MAKE) --no-print-directory ingredients
	@echo "   Packing '$@'..."
	@chmod +x scripts/start-cli-docker.sh
	@scripts/start-cli-docker.sh s9pk pack --icon icon.svg -o $@

$(BASE_NAME)_%.s9pk: $(INGREDIENTS) $(GIT_DEPS)
	@$(MAKE) --no-print-directory ingredients
	@echo "   Packing '$@'..."
	@chmod +x scripts/start-cli-docker.sh
	@scripts/start-cli-docker.sh s9pk pack --icon icon.svg --arch=$* -o $@

check-deps:
	@command -v start-cli >/dev/null || \
		(echo "Error: start-cli not found. See packages/start9/README.md" && exit 1)
	@command -v npm >/dev/null || \
		(echo "Error: npm not found. Please install Node.js and npm." && exit 1)
	@command -v tar2sqfs >/dev/null && command -v mksquashfs >/dev/null || \
		(echo "Error: tar2sqfs/mksquashfs not found. Run: sudo apt install squashfs-tools squashfs-tools-ng" && exit 1)
	@chmod +x scripts/docker.sh scripts/start-cli-docker.sh
	@scripts/docker.sh info >/dev/null
