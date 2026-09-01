# Circle companion mesh node - POSIX dev build.
# Needs: libsodium-dev, libaether-protocol (built from aether-protocol/c).
CC ?= cc
CFLAGS ?= -O2 -Wall -Wextra

circle_mesh_node: circle_mesh_node.c
	$(CC) $(CFLAGS) -o $@ $< -lsodium -laether-protocol

clean:
	rm -f circle_mesh_node
