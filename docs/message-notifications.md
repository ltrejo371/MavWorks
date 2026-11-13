# Message notifications and contact information

Signed-in users have a message notification bell on desktop and mobile. It shows the total unread incoming messages and opens links grouped by conversation. New arrivals produce an in-app alert while browsing other pages. The bell refreshes every five seconds while the app is open; the existing conversation view polls every three seconds. Inbox cards also show unread counts when loaded or refreshed.

Opening or refreshing a conversation acknowledges incoming messages through the last message loaded in that view. Messages arriving after that point remain unread. Read state is persisted, so notifications survive sign-out and refresh. Sending a message does not acknowledge it for its recipient. Notification queries and acknowledgements enforce participant access and hide conversations blocked in either direction. Closed conversations can still be read. These are in-app notifications, not email or operating-system push notifications.

Flyway V15 adds `messages.read_at` and its lookup index. Existing messages are marked read during the migration to avoid notifying users about their entire history. New messages start unread. The default local Hibernate schema-update profile adds the column but does not perform that historical backfill; existing local history will appear unread unless acknowledged or backfilled. No live database was modified during implementation.

Dashboard → Edit profile includes optional contact information (up to 255 characters), such as a preferred email or phone number. Saving updates both the dashboard and existing profile display. Clearing the field removes the information. The field explains that it is shown on the user's profile. It does not change the account's sign-in email or verification state.
