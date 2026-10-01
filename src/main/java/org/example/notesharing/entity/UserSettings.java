package org.example.notesharing.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.notesharing.enums.Theme;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "User_Settings")
public class UserSettings {
    @Id
    @Column(name = "UserID")
    private Long userId;

    @OneToOne
    @MapsId
    @JoinColumn(name = "UserID")
    private User user;

    private Theme theme;
    //private Languages language;
    private Boolean twoFactorEnabled;
    private Boolean notificationEnabled;


}
