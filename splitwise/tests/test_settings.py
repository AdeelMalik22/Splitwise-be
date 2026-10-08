from django.test import SimpleTestCase


class SettingsTests(SimpleTestCase):
    def test_jwt_access_lifetime_is_configurable(self):
        from django.conf import settings

        self.assertEqual(settings.SIMPLE_JWT['ACCESS_TOKEN_LIFETIME'].total_seconds(), 3600)

    def test_static_and_media_roots_are_configured(self):
        from django.conf import settings

        self.assertEqual(settings.STATIC_ROOT.name, 'staticfiles')
        self.assertEqual(settings.MEDIA_ROOT.name, 'media')
