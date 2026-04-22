package com.quantummpv.app.database.converters

import androidx.room.TypeConverter
import com.quantummpv.app.domain.network.NetworkProtocol

/**
 * Type converter for NetworkProtocol enum
 */
class NetworkProtocolConverter {
  @TypeConverter
  fun fromNetworkProtocol(protocol: NetworkProtocol): String = protocol.name

  @TypeConverter
  fun toNetworkProtocol(value: String): NetworkProtocol = NetworkProtocol.valueOf(value)
}

