package org.jetbrains.plugins.scala

import java.net.{URL, URI}
import javax.swing.event.HyperlinkEvent
import com.intellij.notification.{NotificationListener, NotificationType, Notification, Notifications}
import java.awt.{Toolkit, Desktop}
import java.awt.datatransfer.StringSelection
import org.intellij.lang.annotations.Language

/**
 * Pavel Fatin
 */

object DesktopUtils {
  @Language("HTML")
  private val MessageFormat = """
  <html>
  <body>
  Unable to launch web browser, please manually open:<br>
  %1$s (<a href="%1$s">copy to clipboard</a>)>
  </body>
  </html>
  """

  def browse(url: URL) {
    browse(url.toExternalForm)
  }

  def browse(url: String) {
    val supported = Desktop.isDesktopSupported && Desktop.getDesktop.isSupported(Desktop.Action.BROWSE)

    if(supported)
      Desktop.getDesktop.browse(new URI(url))
    else
      Notifications.Bus.notify(new Notification("scala", "Problem opening web page",
        MessageFormat.format(url), NotificationType.WARNING, Listener))
  }

   private object Listener extends NotificationListener {
    def hyperlinkUpdate(notification: Notification, event: HyperlinkEvent) {
      Option(event.getURL).foreach { url =>
         val clipboard = Toolkit.getDefaultToolkit.getSystemClipboard
         clipboard.setContents(new StringSelection(url.toExternalForm), null)
      }
    }
  }
}