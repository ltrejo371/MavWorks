package com.mavworks.repository;

import com.mavworks.model.*;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ListingRepository extends JpaRepository<Listing, Long> {
    List<Listing> findByOwnerId(Long ownerId);
    List<Listing> findByStatus(ListingStatus status);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "images")
    @Query("""
            select l from Listing l
            where l.status = :status
              and (:keyword = '' or locate(lower(:keyword), lower(l.name)) > 0
                   or locate(lower(:keyword), lower(l.description)) > 0)
              and (:category is null or l.category = :category)
              and (:type is null or l.type = :type)
            order by l.createdAt desc, l.id desc
            """)
    List<Listing> searchPublished(@Param("status") ListingStatus status,
            @Param("keyword") String keyword, @Param("category") ListingCategory category,
            @Param("type") ListingType type);
}
