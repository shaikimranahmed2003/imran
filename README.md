# TGNPDCL Employee Salary App

Three parts:

| Folder | What it is |
|---|---|
| `web/TGNPDCL_Payroll_App.html` | Your payroll app, with a new **Employee Mobile App** panel on the Payslip tab |
| `server/` | Small API (Node 18+, no dependencies). HR publishes payslips here; the phone app reads them |
| `android/` | The employee Android app (Kotlin, Jetpack Compose) |

## 1. Run the server
```
cd server
ADMIN_KEY="choose-a-long-secret" DATA_DIR=./data node server.js
```
- `ADMIN_KEY` (required, 12+ characters) is what the payroll app uses to publish. Keep it private.
- `DATA_DIR` must be on persistent storage (payslips and password hashes live there).
- `DEFAULT_PASSWORD` defaults to `npdcl`. Employees must change it on first sign-in.
- For real use the server must be reachable over **HTTPS** (put it behind Caddy/nginx, or use a host that provides HTTPS). A `Dockerfile` is included.

## 2. Publish payslips (each month)
Open `web/TGNPDCL_Payroll_App.html` -> **Payslip** tab -> enter the server address and admin key ->
choose the month -> **Publish month to app**. Republishing a month replaces it.
If an employee forgets their password: same panel -> **Reset employee password**.

## 3. Build the APK
**Without installing anything:** put this whole folder in a GitHub repository, open the **Actions** tab,
run **Build Android APK**, enter your server address (https://...), then download `TGNPDCL-Salary-apk` from the run.

**With Android Studio:** open the `android` folder, set `SERVER_URL` in `android/gradle.properties`,
then **Build > Build APK(s)**. The APK is in `android/app/build/outputs/apk/debug/`.

Install by copying the APK to a phone (allow "install unknown apps"). For Play Store distribution you would
create a signed release build.
