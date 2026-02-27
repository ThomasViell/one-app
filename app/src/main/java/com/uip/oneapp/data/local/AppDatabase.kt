package com.uip.oneapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.uip.oneapp.data.local.dao.DamageDao
import com.uip.oneapp.data.local.dao.InspectionDao
import com.uip.oneapp.data.local.dao.NoteDao
import com.uip.oneapp.data.local.dao.PipeDao
import com.uip.oneapp.data.local.dao.ProjectDao
import com.uip.oneapp.data.local.entity.DamageEntity
import com.uip.oneapp.data.local.entity.InspectionEntity
import com.uip.oneapp.data.local.entity.NoteEntity
import com.uip.oneapp.data.local.entity.PipeEntity
import com.uip.oneapp.data.local.entity.ProjectEntity

@Database(
    entities = [
        ProjectEntity::class,
        PipeEntity::class,
        InspectionEntity::class,
        DamageEntity::class,
        NoteEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun pipeDao(): PipeDao
    abstract fun inspectionDao(): InspectionDao
    abstract fun damageDao(): DamageDao
    abstract fun noteDao(): NoteDao

    companion object {

        // === ONE.APP Legacy Migrations (beibehalten) ===

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE damages ADD COLUMN annotatedPhotoPath TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE projects ADD COLUMN videoQuality TEXT NOT NULL DEFAULT 'HD'")
                database.execSQL("ALTER TABLE projects ADD COLUMN videoOverlay INTEGER NOT NULL DEFAULT 1")
            }
        }

        // === Merge-Migration: ONE.APP v5 -> v6 (DIN 13508-2 hierarchy) ===

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {

                // 1. Neue Tabelle: pipes
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS pipes (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        projectId INTEGER NOT NULL,
                        designation TEXT NOT NULL DEFAULT '',
                        startNode TEXT NOT NULL DEFAULT '',
                        endNode TEXT NOT NULL DEFAULT '',
                        streetName TEXT NOT NULL DEFAULT '',
                        material TEXT NOT NULL DEFAULT '',
                        profile TEXT NOT NULL DEFAULT '',
                        nominalWidth INTEGER,
                        height INTEGER,
                        length REAL,
                        constructionYear INTEGER,
                        rehabilitationYear INTEGER,
                        `usage` TEXT NOT NULL DEFAULT '',
                        drainageArea TEXT NOT NULL DEFAULT '',
                        status TEXT NOT NULL DEFAULT 'PENDING',
                        createdAt INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY (projectId) REFERENCES projects(id) ON DELETE CASCADE
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_pipes_projectId ON pipes(projectId)")

                // 2. Neue Tabelle: inspections
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS inspections (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        pipeId INTEGER NOT NULL,
                        inspectionDate TEXT NOT NULL DEFAULT '',
                        inspectorName TEXT NOT NULL DEFAULT '',
                        weatherCondition TEXT NOT NULL DEFAULT '',
                        direction TEXT NOT NULL DEFAULT 'DOWNSTREAM',
                        method TEXT NOT NULL DEFAULT 'VIDEO',
                        startMeter REAL NOT NULL DEFAULT 0,
                        endMeter REAL NOT NULL DEFAULT 0,
                        videoFileName TEXT NOT NULL DEFAULT '',
                        videoQuality TEXT NOT NULL DEFAULT 'HD',
                        videoOverlay INTEGER NOT NULL DEFAULT 1,
                        cameraType TEXT NOT NULL DEFAULT '',
                        cameraSerialNumber TEXT NOT NULL DEFAULT '',
                        status TEXT NOT NULL DEFAULT 'IN_PROGRESS',
                        remarks TEXT NOT NULL DEFAULT '',
                        createdAt INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY (pipeId) REFERENCES pipes(id) ON DELETE CASCADE
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_inspections_pipeId ON inspections(pipeId)")

                // 3. Bestehende damages Tabelle erweitern
                database.execSQL("ALTER TABLE damages ADD COLUMN inspectionId INTEGER")
                database.execSQL("ALTER TABLE damages ADD COLUMN positionEnd REAL")
                database.execSQL("ALTER TABLE damages ADD COLUMN mainCode TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE damages ADD COLUMN mainCodeName TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE damages ADD COLUMN characterization1 TEXT")
                database.execSQL("ALTER TABLE damages ADD COLUMN char1Name TEXT")
                database.execSQL("ALTER TABLE damages ADD COLUMN characterization2 TEXT")
                database.execSQL("ALTER TABLE damages ADD COLUMN char2Name TEXT")
                database.execSQL("ALTER TABLE damages ADD COLUMN quantification1 TEXT")
                database.execSQL("ALTER TABLE damages ADD COLUMN quant1Name TEXT")
                database.execSQL("ALTER TABLE damages ADD COLUMN quantification2 TEXT")
                database.execSQL("ALTER TABLE damages ADD COLUMN quant2Name TEXT")
                database.execSQL("ALTER TABLE damages ADD COLUMN clockPositionStart TEXT")
                database.execSQL("ALTER TABLE damages ADD COLUMN clockPositionEnd TEXT")
                database.execSQL("ALTER TABLE damages ADD COLUMN jointNumber INTEGER")
                database.execSQL("ALTER TABLE damages ADD COLUMN continuous INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE damages ADD COLUMN damageClass INTEGER")
                database.execSQL("ALTER TABLE damages ADD COLUMN videoTimestamp INTEGER")
                database.execSQL("ALTER TABLE damages ADD COLUMN legacyDamageType TEXT")
                database.execSQL("ALTER TABLE damages ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_damages_inspectionId ON damages(inspectionId)")

                // 4. Legacy-Daten migrieren:
                //    Fuer jedes bestehende Project eine Default-Pipe und Inspection anlegen
                database.execSQL("""
                    INSERT INTO pipes (projectId, designation, startNode, endNode, material,
                                       nominalWidth, length, status, createdAt)
                    SELECT id,
                           startpunkt || '-' || endpunkt,
                           startpunkt, endpunkt, material,
                           CAST(durchmesser AS INTEGER),
                           CAST(inspektionslaenge AS REAL),
                           'COMPLETED',
                           createdAt
                    FROM projects WHERE startpunkt != '' OR endpunkt != ''
                """)

                database.execSQL("""
                    INSERT INTO inspections (pipeId, inspectionDate, inspectorName,
                                             weatherCondition, videoQuality, videoOverlay,
                                             cameraType, status, createdAt)
                    SELECT p.id,
                           pr.inspektionsdatum, pr.inspektor,
                           pr.wetter, pr.videoQuality, pr.videoOverlay,
                           pr.kameratyp, 'COMPLETED', pr.createdAt
                    FROM pipes p
                    JOIN projects pr ON p.projectId = pr.id
                """)

                // 5. Damages mit den neuen inspectionIds verknuepfen
                //    + Legacy damageType in legacyDamageType sichern
                database.execSQL("""
                    UPDATE damages SET
                        legacyDamageType = damageType,
                        mainCode = 'BDE',
                        mainCodeName = 'Bemerkung',
                        inspectionId = (
                            SELECT i.id FROM inspections i
                            JOIN pipes p ON i.pipeId = p.id
                            WHERE p.projectId = damages.projectId
                            LIMIT 1
                        )
                """)
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE projects ADD COLUMN latitude REAL")
                database.execSQL("ALTER TABLE projects ADD COLUMN longitude REAL")
                database.execSQL("ALTER TABLE projects ADD COLUMN mapImagePath TEXT")
            }
        }

        fun create(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "oneapp_database"
            ).addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7).fallbackToDestructiveMigration().build()
        }
    }
}
