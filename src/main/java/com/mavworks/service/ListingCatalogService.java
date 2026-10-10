package com.mavworks.service;

import com.mavworks.dto.ListingSummary;
import com.mavworks.dto.SearchFilter;
import com.mavworks.model.ListingType;
import com.mavworks.model.ListingStatus;
import com.mavworks.repository.ListingRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only catalog used by the Home marketplace. */
@Service
public class ListingCatalogService {
    private final ListingRepository listings;

    public ListingCatalogService(ListingRepository listings) {
        this.listings = listings;
    }

    @Transactional(readOnly = true)
    public List<ListingSummary> search(SearchFilter filter, ListingType type) {
        String keyword = filter.keyword() == null ? "" : filter.keyword().trim();
        return listings.searchPublished(ListingStatus.PUBLISHED, keyword, filter.category(), type)
                .stream().map(listing -> new ListingSummary(listing.getId(), listing.getName(),
                        listing.getDescription(), listing.getPrice(), listing.getCategory(),
                        listing.getType())).toList();
    }
}
