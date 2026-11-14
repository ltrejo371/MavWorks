package com.mavworks.service;

import com.mavworks.dto.ListingSummary;
import com.mavworks.dto.SearchFilter;
import com.mavworks.model.ListingType;
import com.mavworks.model.ListingStatus;
import com.mavworks.model.Listing;
import com.mavworks.repository.ListingRepository;
import java.time.*;
import java.util.List;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only catalog used by the Home marketplace. */
@Service
public class ListingCatalogService {
    public enum SortOption { NEWEST, PRICE_LOW_TO_HIGH, PRICE_HIGH_TO_LOW, MOST_VIEWED }
    public enum DateRange { ANY_TIME, TODAY, LAST_7_DAYS, LAST_30_DAYS, LAST_365_DAYS, CUSTOM }
    private static final int PAGE_SIZE = 12;
    private static final ZoneId MARKET_ZONE = ZoneId.of("America/Chicago");
    private final ListingRepository listings;

    public ListingCatalogService(ListingRepository listings) {
        this.listings = listings;
    }

    @Transactional(readOnly = true)
    public List<ListingSummary> search(SearchFilter filter, ListingType type) {
        String keyword = filter.keyword() == null ? "" : filter.keyword().trim();
        return listings.searchPublished(ListingStatus.ACTIVE, keyword, filter.category(), type, filter.from(), filter.until(), filter.sort().ordering())
                .stream().map(listing -> new ListingSummary(listing.getId(), listing.getName(),
                        listing.getDescription(), listing.getPrice(), listing.getCategory(),
                        listing.getType(), listing.getImages().isEmpty() ? null
                                : listing.getImages().getFirst().getStorageReference(),
                        listing.getViewCount(), listing.getCreatedAt())).toList();
    }

    @Transactional(readOnly = true)
    public Page<ListingSummary> search(SearchFilter filter, ListingType type, int page) {
        if (page < 0) throw new IllegalArgumentException("Page must not be negative.");
        String keyword = filter.keyword() == null ? "" : filter.keyword().trim();
        return searchPage(keyword, filter.category(), type, filter.from(), filter.until(),
                filter.sort().ordering(), page);
    }

    private Page<ListingSummary> searchPage(String keyword, com.mavworks.model.ListingCategory category,
            ListingType type, Instant from, Instant until, Sort ordering, int page) {
        Page<Listing> result = listings.searchPublishedPage(ListingStatus.ACTIVE, keyword,
                category, type, from, until, PageRequest.of(page, PAGE_SIZE, ordering));
        var imageReferences = new java.util.HashMap<Long, String>();
        if (!result.isEmpty()) {
            for (Object[] row : listings.findImageReferences(result.getContent().stream()
                    .map(Listing::getId).toList())) {
                imageReferences.putIfAbsent((Long) row[0], (String) row[1]);
            }
        }
        return result.map(listing -> new ListingSummary(listing.getId(), listing.getName(),
                listing.getDescription(), listing.getPrice(), listing.getCategory(), listing.getType(),
                imageReferences.get(listing.getId()), listing.getViewCount(), listing.getCreatedAt()));
    }

    @Transactional(readOnly = true)
    public Page<ListingSummary> search(SearchFilter filter, ListingType type, SortOption sort,
            DateRange range, LocalDate fromDate, LocalDate throughDate, int page) {
        if (page < 0) throw new IllegalArgumentException("Page must not be negative.");
        Instant from = null;
        Instant until = null;
        Instant now = Instant.now();
        DateRange selectedRange = range == null ? DateRange.ANY_TIME : range;
        if (selectedRange == DateRange.CUSTOM) {
            if (fromDate == null || throughDate == null || throughDate.isBefore(fromDate)) {
                throw new IllegalArgumentException("Choose a valid start and end date.");
            }
            from = fromDate.atStartOfDay(MARKET_ZONE).toInstant();
            until = throughDate.plusDays(1).atStartOfDay(MARKET_ZONE).toInstant();
        } else if (selectedRange != DateRange.ANY_TIME) {
            if (selectedRange == DateRange.TODAY) {
                from = LocalDate.now(MARKET_ZONE).atStartOfDay(MARKET_ZONE).toInstant();
            } else {
                int days = switch (selectedRange) {
                    case LAST_7_DAYS -> 7;
                    case LAST_30_DAYS -> 30;
                    case LAST_365_DAYS -> 365;
                    default -> throw new IllegalStateException("Unsupported listing date range.");
                };
                from = now.minus(Duration.ofDays(days));
            }
        }
        Sort ordering = switch (sort == null ? SortOption.NEWEST : sort) {
            case NEWEST -> Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
            case PRICE_LOW_TO_HIGH -> Sort.by(Sort.Order.asc("price"), Sort.Order.desc("id"));
            case PRICE_HIGH_TO_LOW -> Sort.by(Sort.Order.desc("price"), Sort.Order.desc("id"));
            case MOST_VIEWED -> Sort.by(Sort.Order.desc("viewCount"), Sort.Order.desc("id"));
        };
        String keyword = filter.keyword() == null ? "" : filter.keyword().trim();
        return searchPage(keyword, filter.category(), type, from, until, ordering, page);
    }
}
