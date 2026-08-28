const { withAndroidManifest } = require('@expo/config-plugins');

module.exports = function withAndroidCleartextTraffic(config) {
  return withAndroidManifest(config, configWithManifest => {
    const application = configWithManifest.modResults.manifest.application?.[0];
    const apiUrl = process.env.EXPO_PUBLIC_API_URL || '';
    const allowCleartext = apiUrl.startsWith('http://');

    if (application) {
      application.$['android:usesCleartextTraffic'] = allowCleartext ? 'true' : 'false';
    }

    return configWithManifest;
  });
};
