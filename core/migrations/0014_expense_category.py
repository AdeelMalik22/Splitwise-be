from django.db import migrations, models


class Migration(migrations.Migration):
    dependencies = [('core', '0013_images')]
    operations = [migrations.AddField(
        model_name='expense', name='category',
        field=models.CharField(choices=[('food', 'Food'), ('travel', 'Travel'), ('rent', 'Rent'), ('entertainment', 'Entertainment'), ('other', 'Other')], default='other', max_length=32),
    )]
