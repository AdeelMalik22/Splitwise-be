from django.db import migrations, models


class Migration(migrations.Migration):
    dependencies = [('user', '0005_images')]
    operations = [migrations.AddField(
        model_name='user', name='session_version',
        field=models.PositiveIntegerField(default=0),
    )]
