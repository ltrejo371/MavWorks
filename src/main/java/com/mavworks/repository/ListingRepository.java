package com.mavworks.repository;

import com.mavworks.model.*;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ListingRepository extends JpaRepository<Listing, Long> {
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "images")
    @Query("select l from Listing l where l.owner.id = :ownerId and l.status = :status and l.expiresAt > CURRENT_TIMESTAMP order by l.createdAt desc, l.id desc")
    List<Listing> findByOwnerIdAndStatusOrderByCreatedAtDescIdDesc(Long ownerId, ListingStatus status);
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "images")
    List<Listing> findByOwnerId(Long ownerId);
    List<Listing> findByStatus(ListingStatus status);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "images")
    @Query("""
            select l from Listing l
            where l.status = :status
              and l.expiresAt > CURRENT_TIMESTAMP
              and (:keyword = '' or locate(lower(:keyword), lower(l.name)) > 0
                   or locate(lower(:keyword), lower(l.description)) > 0)
              and (:category is null or l.category = :category)
              and (:type is null or l.type = :type)
              and l.createdAt >= :from and l.createdAt < :until
            """)
    List<Listing> searchPublished(@Param("status") ListingStatus status,
            @Param("keyword") String keyword, @Param("category") ListingCategory category,
            @Param("type") ListingType type, @Param("from") java.time.Instant from,
            @Param("until") java.time.Instant until, org.springframework.data.domain.Sort sort);

    default List<Listing> searchPublished(ListingStatus status, String keyword, ListingCategory category, ListingType type) {
        var filter = new com.mavworks.dto.SearchFilter(keyword, category);
        return searchPublished(status, keyword, category, type, filter.from(), filter.until(), filter.sort().ordering());
    }

    @Query("select l.viewCount from Listing l where l.id = :id")
    long viewCount(@Param("id") Long id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Listing l where l.id = :id")
    java.util.Optional<Listing> findForManagement(@Param("id") Long id);

    @org.springframework.data.jpa.repository.Modifying
    @Query("update Listing l set l.viewCount = l.viewCount + 1 where l.id = :id and l.owner.id <> :viewerId and l.status <> com.mavworks.model.ListingStatus.REMOVED")
    int recordView(@Param("id") Long id, @Param("viewerId") Long viewerId);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "owner")
    org.springframework.data.domain.Page<Listing> findAllByOrderByCreatedAtDesc(org.springframework.data.domain.Pageable page);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "owner")
    @Query("""
            select l from Listing l
            where (:status is null or l.status = :status)
              and (:status is not null or l.status <> com.mavworks.model.ListingStatus.REMOVED)
              and (:cleanupOnly = false or l.status in
                (com.mavworks.model.ListingStatus.SOLD, com.mavworks.model.ListingStatus.WITHDRAWN,
                 com.mavworks.model.ListingStatus.EXPIRED)
                or (l.status = com.mavworks.model.ListingStatus.ACTIVE and l.expiresAt <= :now))
            order by l.createdAt desc, l.id desc
            """)
    org.springframework.data.domain.Page<Listing> findForModeration(@Param("status") ListingStatus status,
            @Param("cleanupOnly") boolean cleanupOnly, @Param("now") java.time.Instant now,
            org.springframework.data.domain.Pageable page);
}
