# Listing moderation

Administrators can open **Moderate listings** from the sidebar or the link on **Reports** (also accessible from mobile Reports navigation). The route is `/admin/listings`; both the route and service operations require an active administrator account.

The default **Outdated or inactive** filter includes expired listings (including active rows whose expiration time has passed), sold listings, and withdrawn listings. **All current listings** includes every listing not already removed. **Removed listings** shows retained records without another removal action. Results are paginated in groups of 50 with owner, status, posted date, and expiration date.

**Remove listing** opens a confirmation naming the listing and owner. Cancel leaves it unchanged. Confirm sets its existing status to `REMOVED`, hides it from marketplace results, and prevents the owner from editing or renewing it. The record and conversation history are retained. This uses the existing removal service and requires no database migration.
