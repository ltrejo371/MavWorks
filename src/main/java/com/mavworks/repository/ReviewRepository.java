package com.mavworks.repository;

import com.mavworks.model.Review;
import com.mavworks.dto.ReviewSummary;
import java.util.List;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    boolean existsByAuthorIdAndListingId(Long authorId, Long listingId);

    @Query("select r.listing.id from Review r where r.author.id = :authorId")
    List<Long> findReviewedListingIds(@Param("authorId") Long authorId);

    @Query("""
            select new com.mavworks.dto.ReviewSummary(r.id, r.author.username, r.listing.id,
                r.listing.name, r.rating, r.comment, r.createdAt)
            from Review r where r.listing.owner.id = :providerId
            order by r.createdAt desc, r.id desc
            """)
    List<ReviewSummary> findPublicReviews(@Param("providerId") Long providerId);
}
