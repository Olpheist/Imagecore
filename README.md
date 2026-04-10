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

1. From the landing page, click **Login** (or **Dashboard** if already logged in).
2. On the login page, select **Register** to create a new account. Press **Enter** or click the button to submit.
3. After successfully registering, you are redirected back to the **Landing Page**.
4. Log in and click the **Go to Dashboard** button or the **Dashboard** breadcrumb.
5. From the Dashboard, the following cards are available based on your role:
    - **Admin Center** (`ADMIN` only): manage users, roles, and inspect paginated application logs
    - **DICOM Upload** (`CLINICIAN` only): upload one or more `.dcm` files
        - files are stored in S3 and an AWS HealthImaging import job is triggered automatically
    - **My DICOM Images** (`CLINICIAN`, `RESEARCHER`): view your uploaded image series (filename, size, upload date, import status) and delete your own entries
    - **Available Tools** (all authenticated users): browse, create, and delete analysis tools
    - **DICOM Viewer** (all authenticated users): open and inspect medical imaging studies
    - **User Profile** (all authenticated users): view and manage your account details
6. Breadcrumbs at the top of every dashboard page show your current location and allow quick navigation back up the hierarchy.

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

## Feature Checklist

After starting the application, use the checklist below to verify each implemented feature.

#### Authentication and Registration
- [ ] **Register**: Visit `/register`, fill in email, username, and password (min 10 chars). Confirm you are redirected to the landing page on success. Try pressing **Enter** to submit the form.
- [ ] **Login**: Visit `/login`, enter credentials. Confirm you are redirected and the nav menu reflects your logged-in state. Try pressing **Enter** to submit.
- [ ] **Forgot / Reset Password**: Visit `/forgot-password`, enter a registered email. A reset token is written to the database (see backend verification below). Visit `/reset-password?token=<token>` to complete the flow.
- [ ] **JWT-based auth**: All protected API calls use a Bearer token stored in `sessionStorage` (`imagecore.jwt`). You can inspect this in browser DevTools → Application → Session Storage.

#### Dashboard and Role-Gated Access
- [ ] **Dashboard**: After login, navigate to Dashboard from the top-right menu. Cards visible depend on your roles. `ADMIN` has all roles (and can add them to itself via Admin Center).
- [ ] **DICOM Upload** (`CLINICIAN` only): Navigate to the DICOM Upload card. Upload one or more `.dcm` files. Confirm a success response is returned and the import status (e.g. `SUBMITTED`) is shown.
- [ ] **My DICOM Images** (`CLINICIAN`, `RESEARCHER`): Navigate to the My DICOM Images card. Confirm your uploaded series are listed with display name, modality, body part, study date, instance count, and import status. Confirm you can delete your own entries but not others.
    - **Run Tool**: For a `COMPLETED` image set, click **Run Tool**. A modal opens showing tool categories; select a category to see tools within it, then select a tool to submit an analysis job. Confirm the button shows `Submitting…` while the request is processing.
    - **Download Report**: After submitting a job, confirm a **Download Report** button appears on that row. Clicking it redirects to a presigned S3 URL for the report PDF.
    - **Run Tool Workflow**: Confirm the **Run Tool Workflow** button is visible on `COMPLETED` rows (stubbed).
- [ ] **DICOM Viewer** (all authenticated users): Navigate via the dashboard card and confirm the viewer loads.
- [ ] **Available Tools** (all authenticated users): Navigate to the Tools card. Confirm the tool list loads
    - create and delete a tool entry to verify write access.
- [ ] **User Profile** (all authenticated users): Navigate to the Profile card and confirm your account details are displayed.
- [ ] **Breadcrumbs**: Verify that breadcrumbs appear on dashboard sub-pages and navigate correctly when clicked.

#### Admin Center (requires `ADMIN` role)
- [ ] **User management**: Navigate to Admin Center → Users. Confirm all registered users are listed.
- [ ] **Role assignment**: Change a user's roles using the role editor. Confirm the change is reflected on next login.
- [ ] **Delete user**: Delete a non-admin user (best to use an account created via register). Confirm they are removed from the list. (Deleting your own account is blocked.)
- [ ] **Application logs**: Navigate to Admin Center → Logs. Confirm logs are paginated and each entry shows method, path, query parameters, status, and duration. Use the **Purge Logs** button to clear all entries.

---

### Backend / Database Verification

The application runs entirely in Docker. You have three clean ways to inspect what is happening under the hood.

#### 1. In-App Log Viewer (easiest)
Log in as an `ADMIN` user and go to **Admin Center → Logs**.

Every non-static HTTP request made to the backend is logged to the `audit_logs` database table and shown here. Logs are paginated and each entry shows:
- Timestamp
- Authenticated username (or anonymous)
- HTTP method and path
- Query parameters
- HTTP status code
- Request duration (ms)

Use the **Purge Logs** button to clear all log entries.


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


#### API Documentation (Swagger UI)

The full, interactive API reference is available via Swagger UI.

**Hosted (production):**  
[https://imagecore.org/swagger-ui/index.html](https://imagecore.org/swagger-ui/index.html)

**Locally (while the application is running):**  
[http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)

The Swagger UI lists all endpoints, their required roles, and request/response schemas

---


## Remote Connection to Production Database

We have set up a bastion ec2 host within the public subnet of our application. This instance belongs to a security group that only allows inbound ssh connection from whitelisted IP addresses and outbound connections to the postgres database and HTTPS (for downloading necessary packages)

To connect you must also get the public `.pem` key and the password for the database. Once these are obtained you can run the following commands to remote into the EC2 instance and connect to the database using `psql`

```bash
# if you have an existing postgres instance running on pprt 5432
ssh -i ~/.ssh/rds-viewing.pem -L 5433:imagecore-dev-db.c0pcqiscgii2.us-east-1.rds.amazonaws.com:5432 ec2-user@100.54.38.122

# you should be able to use a local postgres download with the above command in a separate terminal otherwise use the following command within the EC2 instance

psql -h imagecore-dev-db.c0pcqiscgii2.us-east-1.rds.amazonaws.com -p 5432 -U imagecore -d imagecoredb
```

> [!NOTE]
> If you move the `.pem` file to the ssh directory and it has too broad of permissions you may get an error saying to restrict permissions to 400


## Licensing

For more information regarding use and commercialisation of this software, you can view the license at [`LICENSE`](LICENSE)
