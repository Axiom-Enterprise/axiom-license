package com.github.axiomc.license.web.view;

import com.github.axiomc.license.common.domain.License;
import com.github.axiomc.license.common.domain.LicenseStatus;
import com.github.axiomc.license.web.auth.Account;
import com.github.axiomc.license.web.auth.Role;
import com.github.axiomc.license.web.panel.AssetHandler;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class Pages {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter MOMENT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);

    private Pages() {
    }

    public static String login(String error) {
        String alert = error == null ? "" : "<p class=\"alert\" role=\"alert\">" + Html.escape(error) + "</p>";
        return """
                <!doctype html>
                <html lang="en">
                <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Sign in · Axiom</title>
                <link rel="stylesheet" href="%1$s">
                </head>
                <body class="gate">
                <main>
                <h1 class="wordmark">Axiom</h1>
                <p class="lede">Licence control</p>
                %2$s
                <form method="post" action="/login" autocomplete="on">
                <label>Username<input name="username" type="text" autocomplete="username" autocapitalize="none" spellcheck="false" required autofocus></label>
                <label>Password<input name="password" type="password" autocomplete="current-password" required></label>
                <button class="btn" type="submit">Sign in</button>
                </form>
                </main>
                </body>
                </html>
                """.formatted(AssetHandler.HREF, alert);
    }

    public static String licenses(Account viewer, List<License> licenses, String notice, String issued) {
        StringBuilder body = new StringBuilder(4096);
        body.append(notice(noticeText(notice)));
        body.append("""
                <section class="issue">
                <h2>Issue a licence</h2>
                <form method="post" action="/licenses" class="row">
                <input type="hidden" name="action" value="issue">
                <label>Product<input name="product" type="text" maxlength="64" required></label>
                <label>Holder<input name="holder" type="text" maxlength="64" required></label>
                <label>Expires<input name="expires" type="date"></label>
                <button class="btn" type="submit">Issue</button>
                </form>
                </section>
                """);
        body.append("<section><h2>").append(licenses.size()).append(licenses.size() == 1 ? " licence" : " licences").append("</h2>");
        if (licenses.isEmpty()) {
            body.append("<p class=\"empty\">No licences yet. Issue the first one above.</p>");
        } else {
            body.append("<div class=\"scroll\"><table><thead><tr><th>Key</th><th>Product</th><th>Holder</th><th>Device</th><th>Expires</th><th>Last seen</th><th>Status</th><th></th></tr></thead><tbody>");
            for (License license : licenses) {
                body.append(row(license, viewer.role(), issued != null && issued.equals(Long.toString(license.id()))));
            }
            body.append("</tbody></table></div>");
        }
        body.append("</section>");
        return layout("Licences", viewer, body.toString());
    }

    public static String accounts(Account viewer, List<Account> accounts, String notice) {
        StringBuilder body = new StringBuilder(2048);
        body.append(notice(noticeText(notice)));
        body.append("""
                <section class="issue">
                <h2>Add an account</h2>
                <form method="post" action="/accounts" class="row">
                <input type="hidden" name="action" value="create">
                <label>Username<input name="username" type="text" pattern="[a-z0-9][a-z0-9._-]{2,31}" autocapitalize="none" spellcheck="false" required></label>
                <label>Password<input name="password" type="password" minlength="12" autocomplete="new-password" required></label>
                <label>Role<select name="role"><option value="MOD">Moderator</option><option value="ADMIN">Admin</option></select></label>
                <button class="btn" type="submit">Add</button>
                </form>
                </section>
                """);
        body.append("<section><h2>").append(accounts.size()).append(accounts.size() == 1 ? " account" : " accounts").append("</h2>");
        body.append("<div class=\"scroll\"><table><thead><tr><th>Username</th><th>Role</th><th>Created</th><th>New password</th><th></th></tr></thead><tbody>");
        for (Account account : accounts) {
            boolean self = account.id() == viewer.id();
            body.append("<tr><td>").append(Html.escape(account.username())).append(self ? " <span class=\"tag\">you</span>" : "").append("</td>");
            body.append("<td>").append(account.role().label()).append("</td>");
            body.append("<td>").append(DAY.format(account.createdAt())).append("</td>");
            body.append("<td><form method=\"post\" action=\"/accounts\" class=\"inline\"><input type=\"hidden\" name=\"action\" value=\"password\"><input type=\"hidden\" name=\"id\" value=\"").append(account.id()).append("\"><input type=\"hidden\" name=\"username\" autocomplete=\"username\" value=\"").append(Html.escape(account.username())).append("\">");
            body.append("<input name=\"password\" type=\"password\" minlength=\"12\" autocomplete=\"new-password\" required aria-label=\"New password\"><button class=\"link\" type=\"submit\">Set</button></form></td>");
            body.append("<td class=\"actions\">").append(self ? "" : action("/accounts", "delete", account.id(), "Remove", true)).append("</td></tr>");
        }
        body.append("</tbody></table></div></section>");
        return layout("Accounts", viewer, body.toString());
    }

    public static String integration(Account viewer, String base, String path, String exchangePublic, String signingPublic) {
        String body = """
                <section>
                <h2>Endpoint</h2>
                <p>Clients send one encrypted POST to this address. Nothing else is exposed.</p>
                <pre>%1$s%2$s</pre>
                </section>
                <section>
                <h2>Public keys</h2>
                <p>Embed both in the product. They are public: they let a client encrypt to this server and verify its answers, nothing more.</p>
                <dl>
                <dt>Exchange (X25519)</dt><dd><pre>%3$s</pre></dd>
                <dt>Signing (Ed25519)</dt><dd><pre>%4$s</pre></dd>
                </dl>
                </section>
                <section>
                <h2>Java client</h2>
                <p>Add the <code>common</code> module and keep one client per process.</p>
                <pre>var client = new LicenseClient(URI.create("%1$s"),
                        "%3$s",
                        "%4$s");

                client.verify(key, "my-product", hardwareId)
                      .thenAccept(response -> System.out.println(response.verdict()));</pre>
                </section>
                """.formatted(Html.escape(base), Html.escape(path), Html.escape(exchangePublic), Html.escape(signingPublic));
        return layout("Integration", viewer, body);
    }

    private static String row(License license, Role role, boolean highlight) {
        StringBuilder row = new StringBuilder(512);
        boolean active = license.status() == LicenseStatus.ACTIVE;
        row.append(highlight ? "<tr class=\"new\">" : "<tr>");
        row.append("<td class=\"key\">").append(Html.escape(license.key())).append("</td>");
        row.append("<td>").append(Html.escape(license.product())).append("</td>");
        row.append("<td>").append(Html.escape(license.holder())).append("</td>");
        row.append("<td class=\"device\">").append(license.hardwareId() == null ? "<span class=\"muted\">unbound</span>" : Html.escape(license.hardwareId())).append("</td>");
        row.append("<td>").append(license.expiresAt() == null ? "<span class=\"muted\">never</span>" : DAY.format(license.expiresAt())).append("</td>");
        row.append("<td>").append(license.lastSeenAt() == null ? "<span class=\"muted\">—</span>" : MOMENT.format(license.lastSeenAt())).append("</td>");
        row.append("<td>").append(status(license)).append("</td>");
        row.append("<td class=\"actions\">");
        row.append(action("/licenses", active ? "revoke" : "restore", license.id(), active ? "Revoke" : "Restore", false));
        if (license.hardwareId() != null) {
            row.append(action("/licenses", "unbind", license.id(), "Unbind", false));
        }
        if (role == Role.ADMIN) {
            row.append(action("/licenses", "delete", license.id(), "Delete", true));
        }
        row.append("</td></tr>");
        return row.toString();
    }

    private static String status(License license) {
        if (license.status() == LicenseStatus.REVOKED) {
            return "<span class=\"status bad\">Revoked</span>";
        }
        if (license.expired(Instant.now())) {
            return "<span class=\"status warn\">Expired</span>";
        }
        return "<span class=\"status ok\">Active</span>";
    }

    private static String action(String path, String action, long id, String label, boolean danger) {
        return "<form method=\"post\" action=\"" + path + "\" class=\"inline\"><input type=\"hidden\" name=\"action\" value=\"" + action + "\"><input type=\"hidden\" name=\"id\" value=\"" + id + "\"><button class=\"link" + (danger ? " danger" : "") + "\" type=\"submit\">" + label + "</button></form>";
    }

    private static String notice(String text) {
        return text == null ? "" : "<p class=\"notice\" role=\"status\">" + text + "</p>";
    }

    private static String noticeText(String code) {
        if (code == null) {
            return null;
        }
        return switch (code) {
            case "issued" -> "Licence issued. The new key is at the top of the list.";
            case "revoked" -> "Licence revoked. Checks for this key now fail.";
            case "restored" -> "Licence restored.";
            case "unbound" -> "Device unbound. The next check binds the key again.";
            case "deleted" -> "Licence deleted.";
            case "created" -> "Account added.";
            case "removed" -> "Account removed and signed out everywhere.";
            case "reset" -> "Password changed and sessions closed.";
            case "taken" -> "That username is already in use.";
            case "self" -> "You cannot remove your own account.";
            case "invalid" -> "Check the form: every field is required and passwords need 12 characters.";
            case "forbidden" -> "Only an admin can do that.";
            default -> null;
        };
    }

    private static String layout(String title, Account viewer, String body) {
        String accountsLink = viewer.role() == Role.ADMIN ? "<a href=\"/accounts\">Accounts</a>" : "";
        return """
                <!doctype html>
                <html lang="en">
                <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>%1$s · Axiom</title>
                <link rel="stylesheet" href="%6$s">
                </head>
                <body class="app">
                <nav class="rail" aria-label="Main">
                <a class="wordmark" href="/licenses">Axiom</a>
                <a href="/licenses">Licences</a>
                %2$s
                <a href="/integration">Integration</a>
                <div class="who">
                <span>%3$s</span>
                <small>%4$s</small>
                <form method="post" action="/logout"><button class="link" type="submit">Sign out</button></form>
                </div>
                </nav>
                <main>
                <h1>%1$s</h1>
                %5$s
                </main>
                </body>
                </html>
                """.formatted(title, accountsLink, Html.escape(viewer.username()), viewer.role().label(), body, AssetHandler.HREF);
    }
}
