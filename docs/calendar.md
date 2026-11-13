# Calendar and appointment workflow

The sidebar Calendar (`/calendar`, also reachable at the old `/bookings` route) loads the signed-in user's availability one month at a time. Date selection uses an inline month calendar with highlighted availability and booked dates. Each day has 48 half-hour slots, displayed as 24 AM or 24 PM buttons at a time in America/Chicago time. Marked slots are persisted; missing slots mean unavailable. Confirmed appointments appear for both participants, with check marks on their calendar dates.

Listing appointment requests require an existing conversation. Either participant can request an appointment through that conversation's **+ → Request Appointment** action; products and services are supported. Requested slots must be consecutive and available on the requesting user's calendar. The other participant can accept or decline in the conversation. Declining deletes the request. Confirming reserves both participants across all listings, with participant locks preventing concurrent overlapping confirmations.

Incoming requests show the recipient whether the entire appointment falls within their saved availability, in both Messages and Calendar. Missing any covered half-hour slot produces a warning before the Accept controls. This also applies to reschedule requests and refreshes with the conversation. The notice is advisory: users can accept outside their marked availability, but confirmed appointment conflicts still prevent acceptance. Only the recipient sees their availability assessment; the requester does not receive the other participant's availability details.

Calendar edits save immediately. The scheduling use cases also require Back to discard all changes, so availability edits made on the scheduling screen are drafts: they save in the same transaction as the appointment. Failed requests roll back those edits, and Back discards them. The screen explains this behavior.

Requests expire at the earlier of their start time and 48 hours after creation. Expired requests cannot be accepted even before the background task runs. Every 15 seconds, a background task persists expirations and completes confirmed appointments whose end time has passed. Completion marks product listings Sold and preserves the existing status of service listings. Active services remain available for future appointments. Completion posts a message in the appointment’s conversation and closes that conversation using the existing sale/review flow. Removed listings remain removed and their appointments are cancelled. Jobs resume after application restarts.

Either participant can reschedule a future confirmed appointment from Calendar (including the Bookings alias) or Messages. The scheduling screen opens on the saved date with the existing time slots selected, supports same-day time changes, and returns to its originating page after saving or Back. Rescheduling returns the appointment to Requested and records the proposing participant as requester. Only the other participant can accept or decline in Messages, just as for a new request. The old confirmed reservation is released; the proposed time becomes reserved only upon acceptance, when conflicts are checked again. Rescheduling restarts the 48-hour request window (still capped at the new start time), checks conflicts for both participants while excluding the appointment itself, and saves availability drafts, the new times, and a conversation notice in one transaction. Direct appointment access is restricted to its participants.

Cancel displays a Yes/No confirmation. Yes deletes the appointment from the database and both calendars and posts a cancellation notice in its listing conversation in the same transaction. No makes no changes. Started or completed appointments cannot be rescheduled or cancelled through these controls.

For an active service, selecting Message after the previous conversation closes starts a new conversation for the same buyer. An existing open conversation is reused. Previous messages, appointments, and review eligibility are retained. Products and unavailable services continue to open their existing conversation history.

## Current limitations

Confirmed appointments do not clear the underlying availability selections.
The calendar does not yet distinguish booked time clearly from availability.

## Database upgrade

Flyway migration V11 upgrades old Pending/Approved bookings to Requested/Confirmed, associates legacy bookings with conversations (creating missing conversations), and adds availability storage and query indexes. Cancelled and formerly declined bookings remain cancelled history. V12 converts saved availability to 30-minute slots, keeping a half hour only when both old quarter hours were available. Existing appointment timestamps are preserved. The Supabase profile applies these migrations through Flyway.

The default local H2 profile uses Hibernate schema updates instead of Flyway. Fresh local databases work with the new mappings. An existing local H2 database containing the old bookings table needs the V11 and V12 SQL applied before running the updated app; Hibernate cannot migrate the old status values or backfill conversations. Keep a backup before that local data migration. No existing database was changed as part of this code update.

## Verification and performance

Workflow tests cover privacy, both request directions, products, contiguous slots, midnight boundaries, overlap rejection, expiry, completion notifications, availability persistence, and transaction rollback. View tests cover the AM/PM slot editor, deselection, unavailable-day prompts, and scheduling drafts. A migration test upgrades existing V10 booking rows.

The targets remain calendar loading within two seconds and availability saves within one second. Appointment loading targets three seconds, and appointment status updates target two seconds. Month queries and slot lookups use the availability index. These response-time targets require measurement against the deployed database and network; local correctness tests do not establish production latency. The existing lifecycle sweep runs every 15 seconds, so automatic completion is not covered by the two-second interactive update target. Other open views show changes on refresh.
