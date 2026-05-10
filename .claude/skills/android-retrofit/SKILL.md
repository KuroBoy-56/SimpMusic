---
name: android-retrofit
description: Expert guidance on setting up and using Retrofit for type-safe HTTP networking in Android. Covers service definitions, coroutines, OkHttp configuration, and Hilt integration.
---

# Android Networking with Retrofit

## Instructions

When implementing network layers using **Retrofit**, follow these modern Android best practices (2025).

### 1. URL Manipulation
Retrofit allows dynamic URL updates through replacement blocks and query parameters.

* **Dynamic Paths**: Use `{name}` in the relative URL and `@Path("name")` in parameters.
* **Query Parameters**: Use `@Query("key")` for individual parameters.
* **Complex Queries**: Use `@QueryMap Map<String, String>` for dynamic sets of parameters.

```kotlin
interface SearchService {
    @GET("group/{id}/users")
    suspend fun groupList(
        @Path("id") groupId: Int,
        @Query("sort") sort: String?,
        @QueryMap options: Map<String, String> = emptyMap()
    ): List<User>
}