package com.example.munandydrive.data.local

import androidx.room.TypeConverter
import com.example.munandydrive.data.model.UserRole
import com.example.munandydrive.data.model.RouteStatus
import com.example.munandydrive.data.model.StopStatus

class Converters {
    @TypeConverter
    fun fromUserRole(value: UserRole): String = value.name

    @TypeConverter
    fun toUserRole(value: String): UserRole = UserRole.valueOf(value)

    @TypeConverter
    fun fromRouteStatus(value: RouteStatus): String = value.name

    @TypeConverter
    fun toRouteStatus(value: String): RouteStatus = RouteStatus.valueOf(value)

    @TypeConverter
    fun fromStopStatus(value: StopStatus): String = value.name

    @TypeConverter
    fun toStopStatus(value: String): StopStatus = StopStatus.valueOf(value)
}
