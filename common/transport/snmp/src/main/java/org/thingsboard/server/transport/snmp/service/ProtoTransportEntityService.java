/**
 * Copyright © 2016-2021 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.server.transport.snmp.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.device.data.DeviceData;
import org.thingsboard.server.common.data.device.data.DeviceTransportConfiguration;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.DeviceProfileId;
import org.thingsboard.server.common.data.security.DeviceCredentials;
import org.thingsboard.server.common.transport.TransportService;
import org.thingsboard.server.common.transport.util.DataDecodingEncodingService;
import org.thingsboard.server.gen.transport.TransportProtos;
import org.thingsboard.server.queue.util.TbSnmpTransportComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@TbSnmpTransportComponent
@Service
@RequiredArgsConstructor
public class ProtoTransportEntityService {
    private final TransportService transportService;
    private final DataDecodingEncodingService dataDecodingEncodingService;

    public Device getDeviceById(DeviceId id) {
        TransportProtos.GetDeviceResponseMsg deviceProto = transportService.getDevice(TransportProtos.GetDeviceRequestMsg.newBuilder()
                .setDeviceIdMSB(id.getId().getMostSignificantBits())
                .setDeviceIdLSB(id.getId().getLeastSignificantBits())
                .build());

        if (deviceProto == null) {
            return null;
        }

        DeviceProfileId deviceProfileId = new DeviceProfileId(new UUID(
                deviceProto.getDeviceProfileIdMSB(), deviceProto.getDeviceProfileIdLSB())
        );

        Device device = new Device();
        device.setId(id);
        device.setDeviceProfileId(deviceProfileId);

        DeviceTransportConfiguration deviceTransportConfiguration = (DeviceTransportConfiguration) dataDecodingEncodingService.decode(
                deviceProto.getDeviceTransportConfiguration().toByteArray()
        ).orElseThrow(() -> new IllegalStateException("Can't find device transport configuration"));

        DeviceData deviceData = new DeviceData();
        deviceData.setTransportConfiguration(deviceTransportConfiguration);
        device.setDeviceData(deviceData);

        return device;
    }

    public DeviceCredentials getDeviceCredentialsByDeviceId(DeviceId deviceId) {
        TransportProtos.GetDeviceCredentialsResponseMsg deviceCredentialsResponse = transportService.getDeviceCredentials(
                TransportProtos.GetDeviceCredentialsRequestMsg.newBuilder()
                        .setDeviceIdMSB(deviceId.getId().getMostSignificantBits())
                        .setDeviceIdLSB(deviceId.getId().getLeastSignificantBits())
                        .build()
        );

        return (DeviceCredentials) dataDecodingEncodingService.decode(deviceCredentialsResponse.getDeviceCredentialsData().toByteArray())
                .orElseThrow(() -> new IllegalArgumentException("Device credentials not found"));
    }

    public List<UUID> getAllSnmpDevicesIds() {
        List<UUID> result = new ArrayList<>();

        int page = 0;
        int pageSize = 512;
        boolean hasNextPage = true;

        while (hasNextPage) {
            TransportProtos.GetSnmpDevicesResponseMsg responseMsg = requestSnmpDevicesIds(page, pageSize);
            result.addAll(responseMsg.getIdsList().stream()
                    .map(UUID::fromString)
                    .collect(Collectors.toList()));
            hasNextPage = responseMsg.getHasNextPage();
            page++;
        }

        return result;
    }

    private TransportProtos.GetSnmpDevicesResponseMsg requestSnmpDevicesIds(int page, int pageSize) {
        TransportProtos.GetSnmpDevicesRequestMsg requestMsg = TransportProtos.GetSnmpDevicesRequestMsg.newBuilder()
                .setPage(page)
                .setPageSize(pageSize)
                .build();
        return transportService.getSnmpDevicesIds(requestMsg);
    }
}
