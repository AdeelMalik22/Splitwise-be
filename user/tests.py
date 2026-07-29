from django.test import SimpleTestCase
from rest_framework.test import APITestCase
from rest_framework import status


class AuthenticationContractTests(SimpleTestCase):
    def test_public_routes_are_documented(self):
        from django.urls import reverse

        self.assertEqual(reverse('token_obtain_pair'), '/login/')
        self.assertEqual(reverse('token_refresh'), '/login/refresh/')


class AuthenticationIntegrationTests(APITestCase):
    def test_register_login_and_refresh(self):
        payload = {
            'username': 'alice', 'name': 'Alice', 'email': 'alice@example.com',
            'password': 'strong-password-123',
        }
        response = self.client.post('/users/register/', payload, format='json')
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        self.assertNotIn('password', response.data)

        response = self.client.post('/login/', {
            'username': 'alice', 'password': payload['password'],
        }, format='json')
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertIn('access', response.data)
        self.assertIn('refresh', response.data)

        response = self.client.post('/login/refresh/', {
            'refresh': response.data['refresh'],
        }, format='json')
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertIn('access', response.data)
