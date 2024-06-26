package com.example;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import io.quarkus.qute.Template;
import jakarta.ws.rs.core.Response;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Path("/reservation")
public class ReservationApi {
    @Inject
    Template reservation;

    @Inject
    EntityManager entityManager;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public String index() {
        TypedQuery<Restaurant> queryGetAllRestaurant = entityManager.createQuery("SELECT r FROM Restaurant r", Restaurant.class);
        List<Restaurant> restaurants = queryGetAllRestaurant.getResultList();

        return this.reservation.data("restaurants", restaurants).render();
    }

    @GET
    @Path("/{restaurantId}")
    @Produces(MediaType.APPLICATION_JSON)
    public AvailableTablesResponse getAvailableTables(@PathParam("restaurantId") Long restaurantId,
                                 @QueryParam("date") String date,
                                 @QueryParam("time") String time) {
        Restaurant restaurant = entityManager.find(Restaurant.class, restaurantId);
        if (restaurant == null) throw new NotFoundException("Restaurant not found with id " + restaurantId);
        TypedQuery<Reservation> query = entityManager.createQuery(
                "SELECT r FROM Reservation r " +
                        "JOIN r.restaurantTable t " +
                        "WHERE t.restaurant.id = :restaurantId " +
                        "AND r.date = :date " +
                        "AND r.time = :time", Reservation.class);
        query.setParameter("restaurantId", restaurantId);
        query.setParameter("date", LocalDate.parse(date));
        query.setParameter("time", LocalTime.parse(time));
        List<Reservation> reservation = query.getResultList();

        return new AvailableTablesResponse(restaurant.getNumber_of_tables() - reservation.size());
    }


    @POST
    @Transactional
    public Response createReservation(ReservationRequest reservationData) {
        TypedQuery<RestaurantTable> queryGetTable = entityManager.createQuery(
                "SELECT t FROM restaurant_table t WHERE t.restaurant.id = :restaurantId " +
                        "AND NOT EXISTS (SELECT r FROM Reservation r WHERE r.restaurantTable.id = t.id " +
                        "AND r.date = :date AND r.time = :time) " +
                        "ORDER BY t.id DESC",
                RestaurantTable.class
        );
        queryGetTable.setParameter("restaurantId", reservationData.getRestaurantId());
        queryGetTable.setParameter("date", reservationData.getDate());
        queryGetTable.setParameter("time", reservationData.getTime());
        queryGetTable.setMaxResults(1);
        RestaurantTable table = queryGetTable.getSingleResult();
        TypedQuery<RestaurantUser> queryGetUser = entityManager.createQuery("SELECT u FROM restaurant_user u WHERE u.username = :username", RestaurantUser.class);
        queryGetUser.setParameter("username", reservationData.getUsername());
        RestaurantUser user = queryGetUser.getSingleResult();
        Reservation reservation = new Reservation(table, user, reservationData.getDate(), reservationData.getTime());

        entityManager.persist(reservation);
        ReservationDTO reservationDTO = new ReservationDTO(
                reservation.getId(),
                table.getId(),
                user.getId(),
                reservation.getDate(),
                reservation.getTime()
        );
        return Response.status(Response.Status.CREATED).entity(reservationDTO).build();
    }
}
