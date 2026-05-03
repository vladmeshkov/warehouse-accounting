package by.bsuir.warehouse.common.model;

import java.io.Serializable;

/**
 * DTO с профилем пользователя и его статистикой.
 */
public class UserProfile implements Serializable {

    private static final long serialVersionUID = 1L;

    private User user;
    private UserStats stats;

    public UserProfile() {}

    public UserProfile(User user, UserStats stats) {
        this.user = user;
        this.stats = stats;
    }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public UserStats getStats() { return stats; }
    public void setStats(UserStats stats) { this.stats = stats; }

    /**
     * Статистика пользователя.
     */
    public static class UserStats implements Serializable {
        private static final long serialVersionUID = 1L;

        private int totalIncome;
        private int totalOutcome;
        private int totalTransfer;
        private int totalInventory;
        private int totalForecasts;

        public UserStats() {}

        public UserStats(int totalIncome, int totalOutcome, int totalTransfer,
                         int totalInventory, int totalForecasts) {
            this.totalIncome = totalIncome;
            this.totalOutcome = totalOutcome;
            this.totalTransfer = totalTransfer;
            this.totalInventory = totalInventory;
            this.totalForecasts = totalForecasts;
        }

        public int getTotalIncome() { return totalIncome; }
        public void setTotalIncome(int totalIncome) { this.totalIncome = totalIncome; }

        public int getTotalOutcome() { return totalOutcome; }
        public void setTotalOutcome(int totalOutcome) { this.totalOutcome = totalOutcome; }

        public int getTotalTransfer() { return totalTransfer; }
        public void setTotalTransfer(int totalTransfer) { this.totalTransfer = totalTransfer; }

        public int getTotalInventory() { return totalInventory; }
        public void setTotalInventory(int totalInventory) { this.totalInventory = totalInventory; }

        public int getTotalForecasts() { return totalForecasts; }
        public void setTotalForecasts(int totalForecasts) { this.totalForecasts = totalForecasts; }

        public int getTotal() {
            return totalIncome + totalOutcome + totalTransfer + totalInventory + totalForecasts;
        }
    }
}