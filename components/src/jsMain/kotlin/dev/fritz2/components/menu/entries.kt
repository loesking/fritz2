package dev.fritz2.components.menu

import dev.fritz2.components.foundations.*
import dev.fritz2.components.icon
import dev.fritz2.components.linkButton
import dev.fritz2.dom.HtmlTagMarker
import dev.fritz2.dom.html.RenderContext
import dev.fritz2.styling.*
import dev.fritz2.styling.params.BoxParams
import dev.fritz2.styling.params.Style
import dev.fritz2.styling.params.plus
import dev.fritz2.styling.theme.IconDefinition
import dev.fritz2.styling.theme.Icons
import dev.fritz2.styling.theme.Theme
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLButtonElement

/**
 * An interface for renderable sub-components of a Menu all children of a Menu must implement.
 */
@HtmlTagMarker
interface MenuChild {
    fun render(context: RenderContext)
}

/**
 * This class combines the _configuration_ and the core rendering of a [MenuEntry].
 *
 * An entry is a special kind of button consisting of a label and an optional icon used in dropdown menus.
 * Just like a regular button it is clickable and can be enabled/disabled.
 *
 * It can be configured with an _icon_, a _text_ and can be enabled or disabled the same way as a regular button.
 *
 * @param styling Optional style to be applied to the underlying button-element
 */
open class MenuEntry(private val styling: Style<BoxParams> = {}) :
    MenuChild,
    EventProperties<HTMLButtonElement> by EventMixin(),
    FormProperties by FormMixin() {
    val icon = ComponentProperty<(Icons.() -> IconDefinition)?>(null)
    val text = ComponentProperty<String?>(null)

    override fun render(context: RenderContext) {
        context.apply {
            button(Theme().menu.entry + this@MenuEntry.styling) {
                this@MenuEntry.icon.value?.let {
                    icon(Theme().menu.icon) { def(it(Theme().icons)) }
                }
                this@MenuEntry.text.value?.let { span { +it } }
                disabled(this@MenuEntry.disabled.values)
                this@MenuEntry.events.value.invoke(this)
            }
        }
    }
}

/**
 * This class combines the _configuration_ and the core rendering of a [MenuLink].
 *
 * A link entry is a special kind of [linkButton] consisting of a label and an optional icon used in dropdown menus.
 * Just like a regular button it is clickable and can be enabled/disabled.
 *
 * It can be configured with an _icon_, a _text_ and can be enabled or disabled the same way as a regular button.
 *
 * @param styling Optional style to be applied to the underlying button-element
 */
open class MenuLink(private val styling: Style<BoxParams> = {}) :
    MenuChild,
    EventProperties<HTMLAnchorElement> by EventMixin(),
    FormProperties by FormMixin() {
    val icon = ComponentProperty<(Icons.() -> IconDefinition)?>(null)
    val text = ComponentProperty<String?>(null)
    val href = ComponentProperty<String?>(null)
    val target = ComponentProperty<String?>(null)

    override fun render(context: RenderContext) {
        context.apply {
            a(Theme().menu.entry + this@MenuLink.styling) {
                this@MenuLink.icon.value?.let {
                    icon(Theme().menu.icon) { def(it(Theme().icons)) }
                }
                this@MenuLink.text.value?.let { span { +it } }
                this@MenuLink.href.value?.let { href(it) }
                this@MenuLink.target.value?.let { target(it) }
                attr("disabled", this@MenuLink.disabled.values)
                this@MenuLink.events.value.invoke(this)
            }
        }
    }
}

/**
 * This class combines the _configuration_ and the core rendering of a [CustomMenuEntry].
 *
 * A custom menu entry can be any fritz2 component. The component simply wraps any layout in a container and renders it
 * to the menu.
 */
open class CustomMenuEntry(private val styling: Style<BoxParams> = {}) : MenuChild {
    val content = ComponentProperty<RenderContext.() -> Unit> { }

    override fun render(context: RenderContext) {
        context.apply {
            div(Theme().menu.custom + this@CustomMenuEntry.styling) {
                this@CustomMenuEntry.content.value(this)
            }
        }
    }
}

/**
 * This class combines the _configuration_ and the core rendering of a [MenuHeader].
 *
 * A header can be used to introduce a group of menu entries and separate them from the entries above.
 * It simply consists of a styled _text_.
 *
 * @param styling Optional styling to be applied to the underlying div-element
 */
open class MenuHeader(private val styling: Style<BoxParams> = {}) : MenuChild {

    val text = ComponentProperty("")

    override fun render(context: RenderContext) {
        context.apply {
            div(Theme().menu.header + this@MenuHeader.styling) {
                +this@MenuHeader.text.value
            }
        }
    }
}

/**
 * This class combines the _configuration_ and the core rendering of a [MenuDivider].
 *
 * Similar to a header a divider can be used to group entries together.
 * Compared to a header a divider displays a thin line rather than a text.
 *
 * @param styling Optional styling to be applied to the underlying div-element
 */
open class MenuDivider(private val styling: Style<BoxParams> = {}) : MenuChild {

    override fun render(context: RenderContext) {
        context.apply {
            div(Theme().menu.divider + this@MenuDivider.styling) { }
        }
    }
}