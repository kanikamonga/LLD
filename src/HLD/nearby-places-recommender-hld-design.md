# High-Level Design: Nearby Places Recommender System

## Original Problem Statement

Design a scalable Nearby Places Recommender System, similar to what platforms like Yelp, Google Maps, or Facebook Local offer. The system should help users discover relevant places such as restaurants, cafes, salons, stores, gyms, and attractions around their current or specified location.

### Core Functional Requirements

- Given a user's location `(latitude, longitude)`, return a list of nearby places.
- Results should be filtered and sorted by distance, ratings, categories, open hours, etc.
- User should be able to search places by keyword or category.
- Each place should have metadata like name, address, coordinates, category, rating, and images.

### Extension

- Support ranking based on personalization signals such as user preferences, past visits, and ratings.

---

## 1. Design Principle

The central design principle is:

> Nearby search should use a geo-indexed, read-optimized serving path. Personalization should be applied as a bounded re-ranking layer over retrieved candidates, not by running expensive ranking over all places.

The system should first find geographically relevant candidate places, then apply filters and ranking:

```text
location -> geo candidate retrieval -> filters -> ranking -> personalization -> response
```

This keeps latency low while still supporting relevance and personalization.

---

## 2. Scope

### In scope

- Nearby search by latitude/longitude.
- Keyword search near a location.
- Category search near a location.
- Filtering by category, distance, rating, open hours, price, and attributes.
- Sorting by nearest, highest rated, most popular, recommended, and open now.
- Place metadata and details.
- Place images served from CDN.
- User event collection for personalization.
- Personalized re-ranking based on user signals.
- Business/admin place updates.
- Analytics and observability.

### Out of scope for v1

- Real-time navigation.
- Live traffic-aware routing.
- Food ordering or booking.
- Payment processing.
- Full review moderation system.
- Real-time crowd estimation.
- Augmented reality discovery.

---

## 3. Requirements

### Functional requirements

1. **Nearby places search**
   - Input: latitude, longitude, radius.
   - Return nearby places sorted by relevance, distance, rating, or popularity.

2. **Keyword search**
   - Support queries such as:
     - `pizza near me`
     - `cafes open now`
     - `best salons nearby`
   - Search by place name, category, tags, and description.

3. **Filtering**
   - Distance.
   - Category.
   - Rating.
   - Open now.
   - Price range.
   - Amenities.
   - Popularity.

4. **Sorting**
   - Nearest.
   - Highest rated.
   - Most popular.
   - Recommended.
   - Open now.

5. **Place details**
   - Name.
   - Address.
   - Coordinates.
   - Category.
   - Rating.
   - Review count.
   - Images.
   - Opening hours.
   - Contact details.
   - Attributes.

6. **Personalized recommendations**
   - Rank based on:
     - User preferences.
     - Past visits.
     - Past searches.
     - Ratings.
     - Saved places.
     - Dismissed places.
     - Social signals if available.

7. **Business/admin onboarding**
   - Add/update place metadata.
   - Update operating hours.
   - Upload images.
   - Manage category and attributes.

### Non-functional requirements

| Requirement | Target |
|---|---:|
| Search latency | P95 < 200-300 ms |
| Availability | 99.99% |
| Read traffic | Very high |
| Write traffic | Moderate |
| Location query support | Radius and bounding-box search |
| Place metadata consistency | Eventual consistency acceptable for search |
| Personalization freshness | Minutes to hours acceptable |
| Scalability | Millions of places, thousands to millions QPS globally |
| Fault tolerance | Search should degrade gracefully |

---

## 4. Capacity Estimate

Assume a large-scale platform:

```text
Places: 100 million
Active users/day: 50 million
Searches/user/day: 10
Daily searches: 500 million
Average QPS = 500M / 86,400 ~= 5,800 QPS
Peak QPS = 5x average ~= 30,000 QPS
```

Storage:

```text
Place metadata average size: 2 KB
100M places * 2 KB = 200 GB
Images stored separately in object storage/CDN
Search index overhead: 2x-4x metadata size
Total search index: 500 GB - 1 TB+
```

User signals:

```text
User events/day = billions
Examples: searches, impressions, clicks, visits, ratings, saves
```

Design implications:

- Search must be served from geo-indexed search infrastructure.
- The system cannot scan the place database and compute distance for every request.
- Geospatial indexing is required.
- Caching is important for popular areas and common queries.
- Personalization must not make the search path too slow.

---

## 5. API Design

## 5.1 Nearby Search API

```http
GET /v1/places/nearby
```

Example:

```text
/v1/places/nearby
  ?lat=12.9716
  &lng=77.5946
  &radius=3000
  &category=restaurant
  &openNow=true
  &minRating=4.0
  &sort=recommended
  &limit=20
  &cursor=opaque_cursor
```

Response:

```json
{
  "requestId": "req_123",
  "results": [
    {
      "placeId": "place_123",
      "name": "Third Wave Coffee",
      "category": "cafe",
      "address": "MG Road, Bengaluru",
      "lat": 12.975,
      "lng": 77.61,
      "distanceMeters": 850,
      "rating": 4.5,
      "reviewCount": 2300,
      "priceLevel": 2,
      "openNow": true,
      "imageUrl": "https://cdn.example.com/place_123/main.jpg",
      "rankingReason": "Popular cafe nearby"
    }
  ],
  "nextCursor": "opaque_cursor"
}
```

## 5.2 Keyword Search API

```http
GET /v1/places/search
```

Example:

```text
/v1/places/search
  ?q=pizza
  &lat=12.9716
  &lng=77.5946
  &radius=5000
  &openNow=true
  &limit=20
```

This supports:

```text
pizza near me
cafes open now
best salons nearby
```

## 5.3 Place Details API

```http
GET /v1/places/{placeId}
```

Response:

```json
{
  "placeId": "place_123",
  "name": "Third Wave Coffee",
  "description": "Specialty coffee shop",
  "address": "MG Road, Bengaluru",
  "lat": 12.975,
  "lng": 77.61,
  "categories": ["cafe", "coffee", "dessert"],
  "rating": 4.5,
  "reviewCount": 2300,
  "openingHours": {
    "mon": "08:00-22:00",
    "tue": "08:00-22:00"
  },
  "phone": "+91...",
  "website": "https://example.com",
  "images": [
    "https://cdn.example.com/place_123/1.jpg"
  ],
  "attributes": {
    "wifi": true,
    "outdoorSeating": true,
    "parking": false
  }
}
```

## 5.4 User Feedback APIs

```http
POST /v1/places/{placeId}/save
POST /v1/places/{placeId}/rate
POST /v1/events
```

Example event:

```json
{
  "eventType": "PLACE_CLICKED",
  "userId": "user_123",
  "placeId": "place_123",
  "lat": 12.9716,
  "lng": 77.5946,
  "timestamp": "2026-09-24T10:00:00Z"
}
```

---

## 6. Data Model

## 6.1 Place Metadata

Use PostgreSQL for canonical place metadata if strong relational correctness is needed, or a document store if place attributes are highly flexible.

```text
places(
  place_id PK,
  name,
  description,
  address,
  city,
  state,
  country,
  latitude,
  longitude,
  geohash,
  status,
  created_at,
  updated_at
)
```

```text
categories(
  category_id PK,
  name,
  parent_category_id
)
```

```text
place_categories(
  place_id,
  category_id
)
```

```text
place_hours(
  place_id,
  day_of_week,
  open_time,
  close_time,
  timezone
)
```

```text
place_attributes(
  place_id,
  attribute_key,
  attribute_value
)
```

```text
place_images(
  image_id PK,
  place_id,
  image_url,
  type,
  created_at
)
```

## 6.2 Ratings and Reviews

Aggregated rating:

```text
place_ratings(
  place_id,
  rating_sum,
  rating_count,
  average_rating,
  updated_at
)
```

Reviews:

```text
reviews(
  review_id PK,
  place_id,
  user_id,
  rating,
  text,
  created_at,
  status
)
```

For search, only aggregated rating and review count are usually needed.

## 6.3 User Preference Data

```text
user_profiles(
  user_id PK,
  home_geohash,
  preferred_categories,
  price_preference,
  dietary_preferences,
  updated_at
)
```

```text
user_place_interactions(
  user_id,
  place_id,
  event_type,
  timestamp
)
```

Event types:

```text
SEARCHED
IMPRESSION
CLICKED
SAVED
VISITED
RATED
DISMISSED
SHARED
```

At high scale, user events should go to Kafka/data lake rather than only OLTP tables.

---

## 7. Storage Strategy

| Data | Storage | Why |
|---|---|---|
| Canonical place metadata | PostgreSQL / Document DB | Source of truth |
| Geo search index | Elasticsearch/OpenSearch / Solr / custom geospatial index | Fast geo + keyword + filter queries |
| Place images | Object storage + CDN | Large binary assets |
| User events | Kafka + data lake | High-volume clickstream |
| Aggregated ratings | PostgreSQL / Redis / Search index | Fast serving |
| User profile features | Feature store / Redis / Cassandra | Low-latency personalization |
| Analytics | Data warehouse | Offline reports and ML training |

---

## 8. Geospatial Indexing

The key challenge:

> Given latitude and longitude, efficiently find nearby places.

The system should not scan all places and compute distance.

## 8.1 Option 1: Geohash

Convert latitude/longitude to a geohash.

Example:

```text
lat = 12.9716
lng = 77.5946
geohash = tdr1wx
```

Nearby search:

1. Convert user location to geohash.
2. Find places in the same geohash cell.
3. Also search neighboring cells.
4. Compute exact distance using the Haversine formula.
5. Sort/filter results.

Pros:

- Simple.
- Works well with key-value or search systems.
- Easy to shard by geohash prefix.

Cons:

- Cell sizes vary by latitude.
- Radius search needs neighboring cells.
- Boundary cases require careful handling.

## 8.2 Option 2: S2 Geometry

Use Google's S2 library.

S2 maps Earth to hierarchical cells.

Pros:

- Better spherical geometry.
- Good for large-scale geo systems.
- Useful for covering arbitrary radius with cells.

Cons:

- More complex than geohash.

## 8.3 Option 3: OpenSearch Geo Queries

Use `geo_point` and built-in geo-distance query.

Example:

```json
{
  "query": {
    "bool": {
      "filter": [
        {
          "geo_distance": {
            "distance": "3km",
            "location": {
              "lat": 12.9716,
              "lon": 77.5946
            }
          }
        },
        {
          "term": {
            "category": "restaurant"
          }
        }
      ]
    }
  },
  "sort": [
    {
      "_geo_distance": {
        "location": {
          "lat": 12.9716,
          "lon": 77.5946
        },
        "order": "asc",
        "unit": "m"
      }
    }
  ]
}
```

Recommended:

> Use OpenSearch/Elasticsearch for geo + keyword + filtering in the serving path, backed by canonical place storage and event pipelines.

---

## 9. Search Index Document

OpenSearch document:

```json
{
  "placeId": "place_123",
  "name": "Third Wave Coffee",
  "description": "Specialty coffee shop",
  "categories": ["cafe", "coffee", "dessert"],
  "tags": ["wifi", "dessert", "breakfast"],
  "location": {
    "lat": 12.975,
    "lon": 77.61
  },
  "geohash": "tdr1wx",
  "address": "MG Road, Bengaluru",
  "city": "Bengaluru",
  "country": "India",
  "rating": 4.5,
  "reviewCount": 2300,
  "priceLevel": 2,
  "openingHours": {
    "mon": ["08:00-22:00"],
    "tue": ["08:00-22:00"]
  },
  "popularityScore": 0.87,
  "qualityScore": 0.91,
  "imageUrl": "https://cdn.example.com/place_123/main.jpg",
  "status": "ACTIVE",
  "updatedAt": "2026-09-24T10:00:00Z"
}
```

Important indexed fields:

- `location` as `geo_point`
- `name` as full-text
- `categories` as keyword
- `tags` as keyword/full-text
- `rating` numeric
- `reviewCount` numeric
- `priceLevel` numeric
- `status` keyword
- `city` keyword/full-text

---

## 10. High-Level Architecture

```text
Clients / Mobile / Web
        |
        v
CDN / Edge Cache
        |
        v
API Gateway + Auth + Rate Limiting
        |
        +-----------------------+--------------------------+
        |                       |                          |
        v                       v                          v
 Nearby Search Service   Place Detail Service       User Event Service
        |                       |                          |
        v                       v                          v
 Redis Cache             Place Metadata DB          Kafka/Event Bus
        |                       |                          |
        v                       v                          v
OpenSearch Geo Index     Object Storage/CDN     Stream Processing
        |                       |                          |
        v                       v                          v
 Ranking Service         Images/Media            Feature Store
        |                                                  |
        v                                                  v
Personalization Service                           Data Lake/Warehouse
```

Write/indexing side:

```text
Admin/Business Portal
        |
        v
Place Management Service
        |
        v
Canonical Place DB
        |
        v
PlaceUpdated Event
        |
        v
Kafka/Event Bus
        |
        +------------------------+-------------------------+
        |                        |                         |
        v                        v                         v
Search Index Updater     Cache Invalidator        Analytics Pipeline
        |                        |                         |
        v                        v                         v
OpenSearch               Redis                     Data Warehouse
```

---

## 11. Nearby Search Flow

```text
User sends lat/lng/radius/filter
  -> API Gateway
  -> Nearby Search Service
  -> Normalize request
  -> Build cache key
  -> Check Redis
       -> cache hit:
            return cached results
       -> cache miss:
            query OpenSearch geo index
            retrieve top candidate places
            compute exact distance
            apply filters
            call Ranking Service
            optionally personalize results
            cache response
            return results
```

Cache key example:

```text
nearby:{geohash}:{radius}:{category}:{openNow}:{minRating}:{sort}:{page}
```

Use geohash instead of raw lat/lng in the cache key to improve cache hit rate.

Example:

```text
nearby:tdr1wx:3000:restaurant:true:4.0:recommended:1
```

---

## 12. Keyword Search Flow

```text
User searches "pizza near me"
  -> Search Service
  -> Parse query
       keyword = pizza
       location = user location
       intent = food/place
  -> OpenSearch query:
       full-text match on name/tags/categories
       geo-distance filter
       openNow filter
  -> Ranking Service
  -> Return results
```

Query processing may include:

- Spell correction.
- Synonym expansion.
- Category mapping.
- Intent detection.

Example:

```text
"coffee" -> category=cafe OR tags=coffee
"near me" -> use current location
"open now" -> apply open-hours filter
```

---

## 13. Ranking

Ranking combines multiple signals.

### Base ranking formula

```text
score =
  0.25 * distance_score
+ 0.25 * rating_score
+ 0.20 * popularity_score
+ 0.15 * text_relevance_score
+ 0.10 * open_now_score
+ 0.05 * freshness_score
```

Where:

```text
distance_score decreases as distance increases
rating_score considers average rating and review count
popularity_score uses clicks, visits, saves, bookings
text_relevance_score comes from keyword match
open_now_score boosts currently open places
freshness_score boosts recently updated/verified data
```

Do not rank purely by rating because a place with `5.0` rating and `2 reviews` may be worse than a place with `4.6` rating and `10,000 reviews`.

Use Bayesian rating:

```text
weighted_rating =
  (v / (v + m)) * R + (m / (v + m)) * C
```

Where:

```text
R = place average rating
v = review count
C = global average rating
m = minimum review threshold
```

---

## 14. Personalization

Personalization should be applied as a re-ranking layer.

### Signals

User-specific signals:

- Preferred categories.
- Past searches.
- Past clicks.
- Past visits.
- Saved places.
- Ratings.
- Dismissed places.
- Dietary preferences.
- Price preference.
- Home/work location.
- Time-of-day behavior.

Contextual signals:

- Current location.
- Time of day.
- Day of week.
- Weather.
- Device type.
- Travel vs local user.
- Group/family context.

### Personalized re-ranking

Flow:

```text
OpenSearch returns top 200 candidates
  -> Ranking Service computes base scores
  -> Personalization Service fetches user features
  -> ML ranker or rules re-rank top candidates
  -> Return top 20
```

Important design choice:

> Do not run expensive ML ranking over millions of places. First retrieve a candidate set using geo/search index, then personalize only top candidates.

---

## 15. Feature Store

Store low-latency user/place features:

```text
user_features(
  user_id,
  preferred_categories,
  price_preference,
  recent_clicked_categories,
  embedding_vector,
  updated_at
)
```

```text
place_features(
  place_id,
  popularity_score,
  quality_score,
  category_embedding,
  recent_click_count,
  recent_visit_count,
  updated_at
)
```

Feature storage options:

- Redis for ultra-low latency.
- Cassandra/DynamoDB for scalable key-value features.
- Dedicated feature store for online/offline consistency.

---

## 16. Event Pipeline

User events:

```text
SearchPerformed
PlaceImpression
PlaceClicked
PlaceSaved
PlaceVisited
PlaceRated
PlaceShared
PlaceDismissed
```

Pipeline:

```text
Clients
  -> User Event Service
  -> Kafka
  -> Stream Processor
       -> update real-time counters
       -> update popularity scores
       -> update user features
  -> Data Lake
       -> offline ML training
       -> analytics
```

Use at-least-once delivery with idempotent processing.

---

## 17. Place Update Pipeline

When a place is created or updated:

```text
Admin updates place
  -> Place Management Service
  -> Canonical Place DB
  -> Publish PlaceUpdated event
  -> Search Index Updater
  -> Update OpenSearch document
  -> Invalidate Redis cache for affected geohash/category
```

Place images:

```text
Image Upload
  -> Object Storage
  -> Image Processing Service
  -> Resize/compress
  -> CDN
  -> Update image URL in Place DB/Search Index
```

---

## 18. Open Hours Filtering

Open hours are tricky due to:

- Time zones.
- Overnight hours.
- Holidays.
- Temporary closures.
- Special event hours.

For search:

```text
User location/time
  -> determine local timezone of place
  -> evaluate opening schedule
  -> filter openNow=true
```

For performance:

- Precompute open/closed status for near-future time buckets.
- Store regular hours in index.
- Use a separate real-time override store for temporary closures.

Example:

```text
place_open_status:{placeId} = OPEN/CLOSED
```

---

## 19. Caching Strategy

### Search result cache

Cache popular query/geohash/category combinations:

```text
nearby:{geohash}:{radius}:{category}:{filtersHash}:{sort}:{page}
```

TTL:

```text
30-120 seconds for high-traffic geohashes
2-5 minutes for low-traffic geohashes
```

### Place detail cache

```text
place:{placeId}
```

TTL:

```text
5-60 minutes
```

Invalidate on `PlaceUpdated`.

### Feature cache

```text
user_features:{userId}
place_features:{placeId}
```

TTL depends on freshness needs:

```text
minutes to hours
```

### CDN cache

Cache images and static assets aggressively.

---

## 20. Sharding and Partitioning

### Place Metadata DB

Shard by:

```text
placeId
```

or by region/country if regulatory locality is needed.

### Search Index

Partition by:

```text
geographic region
```

Examples:

```text
country
city
S2/geohash prefix
```

Reason:

- Nearby queries are geographically local.
- Geo-based partitioning reduces shard fanout.

Potential issue:

- Dense cities like NYC, Tokyo, and Bengaluru can become hot.

Mitigation:

- Split dense cells.
- Use adaptive geospatial partitioning.
- Add replicas for hot regions.
- Cache popular queries.

### Kafka

Partition user events by:

```text
userId
```

Partition place updates by:

```text
placeId
```

Partition geo analytics by:

```text
geohash prefix
```

### Feature Store

Partition by:

```text
userId
```

for user features.

Partition by:

```text
placeId
```

for place features.

---

## 21. Failure Scenarios

### OpenSearch unavailable

Impact:

- Nearby search degraded.

Handling:

- Serve cached popular nearby searches from Redis.
- Return degraded response for cache misses.
- Do not affect place details or user events.
- Alert and fail over to replica cluster if available.

### Redis unavailable

Impact:

- Higher latency.
- More load on OpenSearch.

Handling:

- Fail fast on Redis calls.
- Use local in-memory cache for hot queries.
- Apply OpenSearch rate/concurrency limits.
- Degrade expensive filters if needed.

### Personalization Service down

Impact:

- Recommendations become less personalized.

Handling:

- Fall back to base ranking using distance, rating, and popularity.
- Search should remain available.
- Mark personalization unavailable in metrics, not user-facing response.

### Feature Store down

Handling:

- Use cached/default user features.
- Fall back to anonymous ranking.
- Do not fail the search request.

### Place Metadata DB down

Impact:

- Place updates/details may fail.
- Search can continue from OpenSearch.

Handling:

- Serve place details from cache/search index if acceptable.
- Degrade admin writes.
- Alert immediately.

### Event pipeline lagging

Impact:

- Popularity and personalization become stale.

Handling:

- Continue serving with older features.
- Alert on Kafka lag.
- Scale consumers.
- Recompute features offline if needed.

---

## 22. Consistency Model

| Operation | Consistency | Reason |
|---|---|---|
| Nearby search | Eventual | Search index may lag place DB |
| Place details | Stronger if read from DB, eventual if read from cache/index | Depends on endpoint |
| Place update | Strong in canonical DB | Admin expects saved updates |
| Search index update | Eventual | Updated via async events |
| Ratings aggregation | Eventual | Aggregates can lag |
| Personalization | Eventual | Feature updates can lag |
| User save/rating | Strong for user action, eventual for search ranking | User should see own action immediately |
| Analytics | Eventual | Reports tolerate delay |

---

## 23. Observability

### Search metrics

```text
nearby_search_qps
keyword_search_qps
search_latency_p50_p95_p99
search_error_rate
zero_result_rate
cache_hit_ratio
opensearch_latency
opensearch_error_rate
```

### Geo metrics

```text
queries_by_geohash
hot_geohash_cells
average_search_radius
candidate_count_per_query
distance_computation_latency
```

### Ranking metrics

```text
ranking_latency
personalization_latency
feature_store_latency
fallback_to_base_ranking_count
click_through_rate
conversion_rate
```

### Data freshness metrics

```text
place_index_lag
rating_aggregation_lag
feature_update_lag
event_pipeline_lag
```

### Business metrics

```text
search_to_click_rate
search_to_visit_rate
save_rate
rating_rate
category_demand
popular_places_by_region
```

### Alerts

Alert on:

- Search P95/P99 latency above SLA.
- OpenSearch error spike.
- Cache hit ratio collapse.
- Zero-result rate spike.
- Personalization fallback spike.
- Kafka lag.
- Hot shard/geohash overload.
- Place update indexing lag.
- Feature store latency spike.

---

## 24. Security and Privacy

- Use TLS everywhere.
- Protect user location data as sensitive data.
- Store only necessary location history.
- Allow users to delete location history.
- Encrypt sensitive user data.
- Apply access controls for business owners.
- Prevent scraping and bot abuse.
- Rate limit public APIs.
- Use signed URLs or CDN protection for private images if needed.
- Follow privacy regulations like GDPR/CCPA.

Important privacy principle:

> Location history is sensitive. Use it only with consent and retain it only as long as needed.

---

## 25. Disaster Recovery

### Multi-AZ

Deploy across multiple availability zones:

```text
API Gateway
Search Service
Ranking Service
Redis
OpenSearch
Kafka
Feature Store
Metadata DB
```

### Multi-region

| Component | Strategy |
|---|---|
| Search | Active-active by region |
| Place metadata | Replicated, region-owned writes if needed |
| User events | Multi-region ingestion |
| Feature store | Regional replicas |
| Images | CDN/global object storage |
| Analytics | Async replicated |

Search can be active-active because it is mostly read-heavy and eventually consistent.

---

## 26. Zero-Downtime Deployment

Use:

- Rolling deployments.
- Canary releases.
- Backward-compatible APIs.
- Index aliases for OpenSearch.
- Dual writes during schema/index migrations.
- Feature flags for ranking changes.
- Graceful shutdown.

OpenSearch migration:

```text
places_v1
places_v2
places_current -> places_v2
```

Build `places_v2`, validate it, then switch alias atomically.

---

## 27. Key Trade-offs

| Decision | Reason | Trade-off |
|---|---|---|
| OpenSearch geo index | Supports geo + keyword + filters | Eventually consistent and operationally complex |
| Redis search cache | Low latency for repeated queries | Stale results and cache invalidation |
| Geohash/S2 partitioning | Efficient nearby lookup | Boundary handling complexity |
| Candidate retrieval + re-ranking | Keeps ML ranking efficient | May miss candidates if retrieval is poor |
| Personalization as optional layer | Keeps search available during ML failures | Less relevant results during fallback |
| Event-driven indexing | Decouples writes from search serving | Index lag |
| CDN for images | Fast image delivery | Cache invalidation complexity |
| Active-active search | High availability and low latency | Regional index freshness differences |

---

## 28. Final Architecture Summary

```text
User
 |
 v
API Gateway
 |
 +------------------------+
 |                        |
 v                        v
Nearby Search Service     Place Detail Service
 |
 v
Redis Cache
 |
 v
OpenSearch Geo Index
 |
 v
Candidate Places
 |
 v
Ranking Service
 |
 +------------------------+
 |                        |
 v                        v
Feature Store        Personalization Service
 |
 v
Ranked Nearby Places
```

Write/update side:

```text
Business/Admin Update
 |
 v
Place Management Service
 |
 v
Canonical Place DB
 |
 v
Kafka PlaceUpdated Event
 |
 +-------------------------+
 |                         |
 v                         v
Search Index Updater       Cache Invalidator
 |
 v
OpenSearch
```

User behavior side:

```text
User Events
 |
 v
Kafka
 |
 +-------------------------+-------------------------+
 |                         |                         |
 v                         v                         v
Real-time Counters     Feature Store Updates     Data Lake
                                                   |
                                                   v
                                             ML Training
```

---

## 29. Senior-Level Closing Statement

A strong interview summary:

> I would design nearby search as a geo-indexed, read-heavy system backed by OpenSearch or a similar search engine using `geo_point`, geohash/S2 partitioning, and Redis caching for popular query cells. Place metadata lives in a canonical database, while the search index is a derived read model updated asynchronously through Kafka. The serving path first retrieves geographically relevant candidates, applies filters like category/open-now/rating, then ranks by distance, rating confidence, popularity, textual relevance, and personalization. Personalization is a re-ranking layer over a bounded candidate set, so failure of the ML/feature system falls back to non-personalized ranking rather than breaking search. This gives low-latency nearby discovery while allowing metadata, ratings, and user signals to update asynchronously.
