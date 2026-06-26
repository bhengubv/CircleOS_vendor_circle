/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * A game in the Circle Play library.
 */
package za.co.circleos.circleplay;

import java.io.File;

final class Game {

    final String name;
    final File exe;        // the Windows executable to run
    final File cover;      // optional cover art alongside it (may not exist)

    Game(String name, File exe, File cover) {
        this.name = name;
        this.exe = exe;
        this.cover = cover;
    }
}
