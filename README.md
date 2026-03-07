[![Review Assignment Due Date](https://classroom.github.com/assets/deadline-readme-button-22041afd0340ce965d47ae6ef1cefeee28c7c493a6346c4f15d667ab976d596c.svg)](https://classroom.github.com/a/SLVYXqRB)

# Project Setup

IntelliJ + WSL users:
- Ensure a Java SDK named exactly 17 exists in Project Structure → SDKs
- Gradle toolchains require this exact name

## Frontend
Directory: `client`

Requires: Node.js v24
```bash
npm install
npm run dev
```

## Backend
Directory: `server`


Requires: Java 17, Docker

1. Link or open the Gradle project
2. Start the development database by running `docker-compose-dev.yml` as a container
3. Run the Spring Boot application from the Gradle project

## Running the Project

Navigate to the hosted link below:  
https://imagecore.org/

To run the application and database locally, you can simply run the following command from the root of the repository

```shell
bash scripts/run_application.sh
```
The `run_application.sh` script is designed to orchestrate the startup of both the Spring Boot backend and the Nuxt frontend. Because it is a Bash script, it requires a Unix-like environment to function correctly.

This script checks to ensure you have the necessary dependencies, builds the frontend, and starts the postgres and application server.

You can view the application locally at [http://localhost:8080](http://localhost:8080)

## Application Navigation Flow

1. From the landing page, click **Login**.
2. On the login page, select **Register**.
3. After successfully registering, you are redirected back to the **Landing Page**.
4. Open the **top-right navigation menu** and select **Dashboard** to access your dashboard.
5. If logged in as admin, select the **Admin Center** to view and manage user accounts and inspect recent log application
6. On the **Dashboard**, if you have the CLINICIAN role, you can access the the Upload DICOM Image widget
7. On the **Dashboard**, if you have the CLINICIAN, RESEARCHER, or ADMIN role, you can access the Tools page
8. On the **Dashboard**, if you have the CLINICIAN, RESEARCHER, or ADMIN role, you can access the DICOM Image Viewer

**Prerequisites by Operating System**

| System  | Tool Required  | Execution Command  |
|---|---|---|
| Windows  | WSL2 (Ubuntu) or Git Bash  | `bash scripts/run_application.sh`  |
| Linux / macOS  | Native Terminal  | `bash scripts/run_application.sh`  |

> [!NOTE]
> If you have another application running on port 8080, stop that process before running this application.


If you created or edited the script on Windows, it likely contains hidden Carriage Return characters `'\r'` that will cause the script to fail in Bash.

**Symptoms of the error if encountered:**  
`scripts/run_application.sh: line 2: $'\r': command not found`  
`set: pipefail: invalid option name`

**To fix:**  
You must convert the file from CRLF to LF (two options)   
Via Command Line (WSL/Git Bash):
```
sudo apt install dos2unix  # If not installed
dos2unix scripts/run_application.sh
```
**Via IDE:**  
Open the script  
Click CRLF in the bottom-right status bar  
Select LF  
Save the file


## Running the project


### Feature Checklist

After starting the application, use the checklist below to verify each implemented feature.

#### Authentication & Registration
- [ ] **Register**:  Visit `/register`, fill in email, username, and password (min 10 chars). Confirm you are redirected to the landing page on success.
- [ ] **Login**: Visit `/login`, enter credentials. Confirm you are redirected and the nav menu reflects your logged-in state.
- [ ] **Forgot / Reset Password**: Visit `/forgot-password`, enter a registered email. A reset token is written to the database (see backend verification below). Visit `/reset-password?token=<token>` to complete the flow.
- [ ] **JWT-based auth**: All protected API calls use a Bearer token stored in `sessionStorage` (`imagecore.jwt`). You can inspect this in browser DevTools → Application → Session Storage.

#### Dashboard & Role-Gated Access
- [ ] **Dashboard**: After login, navigate to Dashboard from the top-right menu. Widgets visible depend on your roles (ADMIN has all roles (and if not, can add them to itself).
- [ ] **DICOM Upload widget**: Only visible if your account has the `CLINICIAN` role. Upload the `test.dcm` file; confirm a success response is returned.
- [ ] **DICOM Viewer**: Accessible to `CLINICIAN`, `RESEARCHER`, and `ADMIN`. Navigate via the dashboard button.
- [ ] **Tools page**: Accessible to `CLINICIAN`, `RESEARCHER`, and `ADMIN`. Lists available tools (currently one), allows creating and deleting tools.

#### Admin Center (requires `ADMIN` role)
- [ ] **User management**: Navigate to Admin Center → Users. Confirm all registered users are listed.
- [ ] **Role assignment**: Change a user's roles using the role editor. Confirm the change is reflected on next login.
- [ ] **Delete user**: Delete a non-admin user (probably best to delete a user you make with register). Confirm they are removed from the list. (Deleting your own account is blocked.)
- [ ] **Application logs**: Navigate to Admin Center → Logs. Filter by username, log level (INFO/WARN/ERROR/DEBUG), and time range. Every API request made to the backend appears here.

---

### Backend / Database Verification

The application runs entirely in Docker. You have three clean ways to inspect what is happening under the hood.

#### 1. In-App Log Viewer (easiest)
Log in as an `ADMIN` user and go to **Admin Center → Logs**.

Every non-static HTTP request made to the backend is logged to the `audit_logs` database table and shown here. Each entry shows:
- Timestamp
- Log level
- Authenticated username (or `\` for anonymous)
- HTTP status, method, path, and client IP


#### 2. Live Server Logs (terminal)
The `run_application.sh` script streams Docker Compose output directly to your terminal. Spring Boot logs every request, Flyway migration, and startup event there. To view logs after startup in a separate terminal:

```bash
docker logs -f imagecore-dev
```

#### 3. Direct Database Access
The PostgreSQL database is exposed on `localhost:5432` while the application is running.

Connect with any PostgreSQL client:

| Setting  | Value       |
|----------|-------------|
| Host     | `localhost` |
| Port     | `5432`      |
| Database | `imagecore` |
| Username | `imagecore` |
| Password | `imagecore` |


#### API Endpoints Summary

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/auth/register` | Public | Register a new user |
| POST | `/api/auth/login` | Public | Login and receive JWT |
| POST | `/api/auth/forgot-password` | Public | Request a password reset |
| POST | `/api/auth/reset-password` | Public | Complete password reset |
| GET | `/api/users/me` | Any authenticated | Get current user profile |
| GET | `/api/users` | ADMIN | List all users |
| PUT | `/api/users/{id}/roles` | ADMIN | Update a user's roles |
| DELETE | `/api/users/{id}` | ADMIN | Delete a user |
| GET | `/api/roles` | ADMIN | List all roles |
| GET | `/api/logs` | ADMIN | Query audit logs |
| GET | `/api/tools` | CLINICIAN, RESEARCHER, ADMIN | List all tools |
| POST | `/api/tools` | CLINICIAN, RESEARCHER, ADMIN | Create a tool |
| DELETE | `/api/tools/{id}` | CLINICIAN, RESEARCHER, ADMIN | Delete a tool |
| GET | `/api/tools/category/{category}` | CLINICIAN, RESEARCHER, ADMIN | Filter tools by category |
| POST | `/api/images/upload` | CLINICIAN | Upload a DICOM file to S3 |

---

## Licensing

For more information regarding use and commercialisation of this software, you can view the license at [`LICENSE`](LICENSE)
