# Commit-Crew-HI

A bakery-focused food ordering and delivery simulator developed for **SOEN 343 – Software Architecture and Design**.

The system allows customers to order bakery products, bakery staff to manage and prepare orders, couriers to deliver them, and operators to monitor the platform.

## Team Members

| Name | Student ID | Role |
|---|---:|---|
| Wiâme Kotbi | 40299105 | Repository Coordinator & Software Developer |
| Maria Laghmari | 40297167 | Team Lead & Software Developer |
| Aya Manguer | 40298292 | Requirements & Documentation Coordinator & Software Developer |
| Anthony Ly | 40282737 | Design Coordinator & Software Developer |
| Quentin Malichecq-Laroche | 40285143 | Quality Coordinator & Software Developer |
| Sukhdarshan Singh | 40240213 | Integration Coordinator & Software Developer |

**Team Leader:** Maria Laghmari

## Main Users

- Customer
- Bakery staff
- Courier
- Platform operator

## Main Features

- User login and role-based access
- Browse bakery menus
- Place and manage orders
- Manage bakery items and availability
- Prepare orders in arrival order
- Assign ready orders to couriers
- Confirm pickup and delivery
- Calculate order prices
- Persist delivered-order charges and a separate operator-refund ledger
- View order history
- Display restaurants, couriers, and orders on the simulator dashboard

## Technology Stack

- **Backend:** Java + Spring Boot
- **Frontend:** HTML, CSS, JavaScript
- **Version Control:** Git + GitHub
- **Modelling:** Draw.io 
- **Testing:** JUnit 5 and Mockito
- **Database:** PostgreSQL (Supabase) through Spring Data JPA

## Project Modules

- **REG** – Registration, Login & Profiles
- **ORD** – Order & Bakery Core
- **DLV** – Courier & Delivery
- **DASH** – Simulator Dashboard
- **PRC** – Pricing, Billing & Order History

## Platform Requirements

- Java 17 or newer
- Windows, macOS, or Linux
- PostgreSQL/Supabase database
- Internet access for Maven dependencies

Maven does not need to be installed because the Maven Wrapper is included.

## Run with Docker

The containerized setup includes Java, Maven and PostgreSQL. The only platform
requirement is Docker Desktop (or Docker Engine with Docker Compose).

From the repository root, run:

```bash
docker compose up --build
```

Wait until the application logs `Tomcat started on port 8080`, then open:

```text
http://localhost:8080/login.html
```

Stop the application with `Ctrl+C`, followed by:

```bash
docker compose down
```

The PostgreSQL data is kept in a Docker volume. To delete that volume and start
with a completely new database, run `docker compose down -v`.

## Database Configuration

Set the Supabase environment variables before running the application.

### Windows PowerShell

```powershell
$env:SUPABASE_DB_URL="jdbc:postgresql://HOST:5432/postgres?sslmode=require"
$env:SUPABASE_DB_USERNAME="YOUR_USERNAME"
$env:SUPABASE_DB_PASSWORD="YOUR_PASSWORD"
```

### macOS/Linux

```bash
export SUPABASE_DB_URL="jdbc:postgresql://HOST:5432/postgres?sslmode=require"
export SUPABASE_DB_USERNAME="YOUR_USERNAME"
export SUPABASE_DB_PASSWORD="YOUR_PASSWORD"
```

## Initial State (Configuration Files)

At startup the application loads its initial state from two configuration files (UC-REG-03, UC-ORD-01):

- `TheBakeway/backend/src/main/resources/config/initialAccounts.txt`: the seeded accounts, at least one per role
- `TheBakeway/backend/src/main/resources/config/initialCity.txt`: the bakeries (name, status, address) and their menu items (name, price, status)

**Starting the application replaces the accounts, bakeries and menus in the database with these files and deletes all orders, events and associated billing ledger entries.** An invalid entry is reported in the console and the previous data is kept. The platform operator can restore the same initial state at any time from the dashboard.

The `order_ledger_entries` table stores a charge with its price breakdown when an order is delivered, and a distinct full-refund entry when an operator refunds it. A refund does not overwrite the original charge; it also transitions the order from `delivered` to `refunded` and records that lifecycle event. The ledger uses CAD amounts with two-decimal database precision.

To start without reloading the data, set this in `TheBakeway/backend/src/main/resources/application.properties`:

```properties
bakeway.load-initial-state=false
```

## Compile and Test

### Windows

```powershell
cd TheBakeway/backend
.\mvnw.cmd clean test
```

### macOS/Linux

```bash
cd TheBakeway/backend
./mvnw clean test
```

The database environment variables must be set: the application context test connects to the database. The tests do not reload the initial state.

### Characterisation Tests (Phase IV refactorings)

Each refactoring of Phase IV is protected by characterisation tests that pin down the behaviour of the code before it changes. They are committed before the refactoring, and pass on the commit before it and on the refactoring commit. They do not need the database.

| Refactoring | Test class |
|---|---|
| R1: Switch Statements -> Extract Class (`OrderAccessPolicy`) | `OrderAccessCharacterisationTest` |

Run all of them (macOS/Linux; on Windows use `.\mvnw.cmd`):

```bash
cd TheBakeway/backend
./mvnw test -Dtest='*CharacterisationTest'
```

Run one refactoring's tests on the commit before it and on the refactoring commit, for example R1:

```bash
R1=$(git log --format=%h --grep="^refactor(R1)" -n 1)
git switch --detach "$R1~1" && (cd TheBakeway/backend && ./mvnw test -Dtest=OrderAccessCharacterisationTest)
git switch --detach "$R1" && (cd TheBakeway/backend && ./mvnw test -Dtest=OrderAccessCharacterisationTest)
git switch main
```

## Run Locally

### Windows

```powershell
cd TheBakeway/backend
.\mvnw.cmd spring-boot:run
```

### macOS/Linux

```bash
cd TheBakeway/backend
./mvnw spring-boot:run
```

Then open:

```text
http://localhost:8080/login.html
```

The application runs on port `8080` by default. Wait for the `LOAD_INITIAL_STATE` line and then `Tomcat started on port 8080` in the terminal before logging in.

After login, every role lands on its dashboard (`/dashboard.html`):

One browser keeps one session, so use separate browsers or private windows to be logged in with several roles at the same time.

## Build

### Windows

```powershell
cd TheBakeway/backend
.\mvnw.cmd clean package
```

### macOS/Linux

```bash
cd TheBakeway/backend
./mvnw clean package
```

Run the generated JAR (with the database environment variables set):

```bash
java -jar target/thebakeway-0.0.1-SNAPSHOT.jar
```

## Demo Accounts

Loaded from `initialAccounts.txt` at startup:

| Role | Username | Password | Notes |
|---|---|---|---|
| Customer | `customer1` | `customer123` | |
| Customer | `customer2` | `customer123` | |
| Bakery staff | `staff1` | `staff123` | Sweet Treats |
| Bakery staff | `staff2` | `staff123` | Crumbs and Co |
| Courier | `courier1` | `courier123` | |
| Courier | `courier2` | `courier123` | Same distance from Sweet Treats as `courier3`: shows the lowest-id tie-break |
| Courier | `courier3` | `courier123` | |
| Operator | `operator1` | `operator123` | |

New customers can also register from the login page.

## Code Metrics

The raw code measurements of Phase IV (Lizard) and the script to repeat them are in [`docs/metrics`](docs/metrics/README.md).

## Repository

This repository contains the source code, tests and configuration files of the project.

The requirements, use cases, architecture decisions and design pattern diagrams are documented in the project report.
