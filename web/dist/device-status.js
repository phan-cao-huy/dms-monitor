// Warning is a connected device with an alert, not a disconnected device.
export const isDeviceOnline = device => ['Online','Warning'].includes(device.status);
export const matchesDeviceStatus = (device,filter) => filter==='all'||(filter==='Online'?isDeviceOnline(device):device.status===filter);
export const connectedCount = devices => devices.filter(isDeviceOnline).length;
