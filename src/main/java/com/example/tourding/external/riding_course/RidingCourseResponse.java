package com.example.tourding.external.riding_course;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class RidingCourseResponse {
    private int currentCount;
    private int matchCount;
    private int page;
    private int perPage;
    private int totalCount;
    private List<Data> data;

    // Getter, Setter
    public int getCurrentCount() {
        return currentCount;
    }

    public void setCurrentCount(int currentCount) {
        this.currentCount = currentCount;
    }

    public int getMatchCount() {
        return matchCount;
    }

    public void setMatchCount(int matchCount) {
        this.matchCount = matchCount;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getPerPage() {
        return perPage;
    }

    public void setPerPage(int perPage) {
        this.perPage = perPage;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }

    public List<Data> getData() {
        return data;
    }

    public void setData(List<Data> data) {
        this.data = data;
    }

    // Inner Class
    public static class Data {
        @JsonProperty("도착점")
        private String arrival;

        @JsonProperty("소개")
        private String description;

        @JsonProperty("소요분")
        private String minutes;

        @JsonProperty("소요시간")
        private String hours;

        @JsonProperty("출발점")
        private String departure;

        @JsonProperty("코스구분명")
        private String courseType;

        @JsonProperty("코스명")
        private String courseName;

        // Getter, Setter
        public String getArrival() {
            return arrival;
        }

        public void setArrival(String arrival) {
            this.arrival = arrival;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getMinutes() {
            return minutes;
        }

        public void setMinutes(String minutes) {
            this.minutes = minutes;
        }

        public String getHours() {
            return hours;
        }

        public void setHours(String hours) {
            this.hours = hours;
        }

        public String getDeparture() {
            return departure;
        }

        public void setDeparture(String departure) {
            this.departure = departure;
        }

        public String getCourseType() {
            return courseType;
        }

        public void setCourseType(String courseType) {
            this.courseType = courseType;
        }

        public String getCourseName() {
            return courseName;
        }

        public void setCourseName(String courseName) {
            this.courseName = courseName;
        }
    }
}
