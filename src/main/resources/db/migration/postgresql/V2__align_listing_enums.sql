ALTER TABLE listings DROP CONSTRAINT listings_category_check;
ALTER TABLE listings ADD CONSTRAINT listings_category_check
    CHECK (category IN ('ART', 'BEAUTY', 'CLOTHING', 'JEWELRY', 'TUTORING', 'GRAPHIC_DESIGN', 'OTHER'));

ALTER TABLE listings DROP CONSTRAINT listings_status_check;
-- Keep legacy drafts and unavailable listings hidden from the public catalog.
UPDATE listings SET status = CASE status
    WHEN 'PUBLISHED' THEN 'ACTIVE'
    WHEN 'DRAFT' THEN 'REMOVED'
    WHEN 'UNAVAILABLE' THEN 'REMOVED'
    ELSE status
END;
ALTER TABLE listings ADD CONSTRAINT listings_status_check
    CHECK (status IN ('ACTIVE', 'SOLD', 'EXPIRED', 'REMOVED'));
