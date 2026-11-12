package io.embodit.suanpan.ui.input

import io.embodit.suanpan.ui.volume.AbacusTarget

/** The volume's live board-input listener, so the debug `replay` command feeds real scene entities to it (template README). */
object DebugInput {
    @Volatile var listener: BoardInputListener<AbacusTarget>? = null
}
