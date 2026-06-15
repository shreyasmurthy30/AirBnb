# AirBnB — Hotel Booking API

A production-ready hotel booking REST API built with Spring Boot featuring dynamic pricing, Stripe payments, async email notifications, Redis caching and rate limiting.

## Tech Stack

- **Backend:** Spring Boot 4.0.6, Java 24
- **Database:** PostgreSQL
- **Cache & Rate Limiting:** Redis
- **Async Notifications:** Apache Kafka
- **Payments:** Stripe Checkout
- **Auth:** JWT
- **Containerization:** Docker, Docker Compose
- **CI:** GitHub Actions
- **Deployment:** Render, Supabase, Upstash

## Key Features

- JWT authentication (signup/login)
- Hotel and room management with role-based access (Guest, Hotel Manager)
- Dynamic pricing engine — occupancy-based, surge, urgency pricing using Strategy pattern
- Stripe Checkout with webhook-based payment confirmation and automatic refunds
- Async email notifications via Kafka (booking confirmed/cancelled)
- Redis caching for hotel search and rate limiting using sliding window algorithm
- Input validation and global exception handling
- Spring Actuator health endpoints
- Automated booking expiry with scheduled cleanup

## Running Locally

### Prerequisites
- Docker Desktop
- Stripe CLI

### Steps

**1. Clone the repo**
```bash
git clone https://github.com/shreyasmurthy30/AirBnb.git
cd AirBnb
```

**2. Create a `.env` file in the root**
```
KAFKA_ENABLED=true
JWT_SECRET=your_jwt_secret
STRIPE_SECRET_KEY=your_stripe_secret_key
STRIPE_WEBHOOK_SECRET=your_stripe_webhook_secret
MAIL_USERNAME=your_email@gmail.com
MAIL_PASSWORD=your_gmail_app_password
```

**3. Start all services**
```bash
docker compose up
```

**4. Forward Stripe webhooks (separate terminal)**
```bash
stripe listen --forward-to localhost:8080/api/v1/webhook/payments
```

API available at `http://localhost:8080/api/v1`  
Swagger UI at `http://localhost:8080/api/v1/swagger-ui.html`

## Architecture

```
Client → Spring Boot API → PostgreSQL
                        → Redis (cache + rate limiting)  
                        → Kafka (async notifications)
                        → Stripe (payments)
```

Kafka is feature-flagged via `KAFKA_ENABLED` — enabled locally via Docker, disabled on Render since free managed Kafka tiers are deprecated.
