from django.test import SimpleTestCase

from splitwise.dbconfig import database_from_url


class DatabaseUrlTests(SimpleTestCase):
    def test_parses_neon_style_url(self):
        cfg = database_from_url('postgresql://user:p%40ss@ep-1.neon.tech/neondb?sslmode=require&channel_binding=require')
        self.assertEqual(cfg['NAME'], 'neondb')
        self.assertEqual(cfg['USER'], 'user')
        self.assertEqual(cfg['PASSWORD'], 'p@ss')
        self.assertEqual(cfg['HOST'], 'ep-1.neon.tech')
        self.assertEqual(cfg['PORT'], '5432')
        self.assertEqual(cfg['OPTIONS'], {'sslmode': 'require'})

    def test_rejects_non_postgres_urls(self):
        with self.assertRaises(ValueError):
            database_from_url('mysql://u:p@h/db')
