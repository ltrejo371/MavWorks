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
            order by l.createdAt desc, l.id desc
            """)
    List<Listing> searchPublished(@Param("status") ListingStatus status,
            @Param("keyword") String keyword, @Param("category") ListingCategory category,
            @Param("type") ListingType type);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Listing l where l.id = :id")
    java.util.Optional<Listing> findForManagement(@Param("id") Long id);

    @org.springframework.data.jpa.repository.Modifying
    @Query("update Listing l set l.viewCount = l.viewCount + 1 where l.id = :id and l.owner.id <> :viewerId and l.status <> com.mavworks.model.ListingStatus.REMOVED")
    int recordView(@Param("id") Long id, @Param("viewerId") Long viewerId);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "owner")
    org.springframework.data.domain.Page<Listing> findAllByOrderByCreatedAtDesc(org.springframework.data.domain.Pageable page);
}
