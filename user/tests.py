from django.test import SimpleTestCase


class AuthenticationContractTests(SimpleTestCase):
    def test_public_routes_are_documented(self):
        from django.urls import reverse

        self.assertEqual(reverse('token_obtain_pair'), '/login/')
        self.assertEqual(reverse('token_refresh'), '/login/refresh/')
