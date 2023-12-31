package lk.ijse.api;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.zaxxer.hikari.HikariDataSource;
import lk.ijse.to.request.LecturerReqTO;
import lk.ijse.to.response.LecturerResTO;
import org.checkerframework.checker.units.qual.A;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import javax.validation.Valid;
import java.io.IOException;
import java.net.URL;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/lecturers")
@CrossOrigin
@Validated
public class LecturerHttpController {

    @Autowired
    private HikariDataSource pool;

    @Autowired
    private Bucket bucket;

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping(consumes = "multipart/form-data", produces = "application/json")
    public LecturerResTO createNewLecturer(@ModelAttribute @Valid LecturerReqTO lecturer){
        try (Connection connection = pool.getConnection()) {
            connection.setAutoCommit(false);

            try {
                PreparedStatement stmInsertLecturer = connection
                        .prepareStatement("INSERT INTO lecturer " +
                                "(name, designation, qulifications, linkedin) " +
                                "VALUES (?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
                stmInsertLecturer.setString(1, lecturer.getName());
                stmInsertLecturer.setString(2, lecturer.getDesignation());
                stmInsertLecturer.setString(3, lecturer.getQualifications());
                stmInsertLecturer.setString(4, lecturer.getLinkedin());
                stmInsertLecturer.executeUpdate();
                ResultSet generatedKeys = stmInsertLecturer.getGeneratedKeys();
                generatedKeys.next();
                int lecturerId = generatedKeys.getInt(1);
                String picture = lecturerId + "-" + lecturer.getName();

                String pictureUrl = null;
                if (lecturer.getPicture() != null && !lecturer.getPicture().isEmpty()) {
                    PreparedStatement stmUpdateLecturer = connection
                            .prepareStatement("UPDATE lecturer SET picture = ? WHERE id = ?");
                    stmUpdateLecturer.setString(1, picture);
                    stmUpdateLecturer.setInt(2, lecturerId);
                    stmUpdateLecturer.executeUpdate();
                }

                final String table = lecturer.getType().equalsIgnoreCase("full-time")
                        ? "full_time_rank": "part_time_rank";
                Statement stm = connection.createStatement();
                ResultSet rst = stm.executeQuery("SELECT `rank` FROM "+ table +" ORDER BY `rank` DESC LIMIT 1");
                int rank;
                if (!rst.next()) rank = 1;
                else rank = rst.getInt("rank") + 1;
                PreparedStatement stmInsertRank = connection
                        .prepareStatement("INSERT INTO "+ table +" (lecturer_id, `rank`) VALUES (?, ?)");
                stmInsertRank.setInt(1, lecturerId);
                stmInsertRank.setInt(2, rank);
                stmInsertRank.executeUpdate();

                if(lecturer.getPicture() !=null && !lecturer.getPicture().isEmpty()){
                    Blob blob = bucket.create(picture, lecturer.getPicture().getInputStream(), lecturer.getPicture().getContentType());
                    pictureUrl = blob.signUrl(1, TimeUnit.DAYS, Storage.SignUrlOption.withV4Signature()).toString();


                }

                connection.commit();
                return new LecturerResTO(lecturerId,
                        lecturer.getName(),
                        lecturer.getDesignation(),
                        lecturer.getQualifications(),
                        lecturer.getType(),
                        pictureUrl,
                        lecturer.getLinkedin());
            }catch (Throwable t){
                connection.rollback();
                throw t;
            }finally {
                connection.setAutoCommit(true);
            }
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @PatchMapping("/{lecturer-id}")
    public void updateLecturerDetails(){
        System.out.println("updateLecturerDetails()");
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{lecturer-id}")
    public void deleteLecturer(@PathVariable("lecturer-id") int lecturerId) {
        try (Connection connection = pool.getConnection()) {
            PreparedStatement stmExists = connection.prepareStatement("SELECT * FROM lecturer WHERE id=?");
            stmExists.setInt(1, lecturerId);
            if (!stmExists.executeQuery().next()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);

            connection.setAutoCommit(false);
            try {

                PreparedStatement stmIdentify = connection
                        .prepareStatement("SELECT l.id, l.name, l.picture, " +
                                "ftr.`rank` AS ftr, ptr.`rank` AS ptr FROM lecturer l " +
                                "LEFT OUTER JOIN full_time_rank ftr ON l.id = ftr.lecturer_id " +
                                "LEFT OUTER JOIN part_time_rank ptr ON l.id = ptr.lecturer_id " +
                                "WHERE l.id = ?");
                stmIdentify.setInt(1, lecturerId);
                ResultSet rst = stmIdentify.executeQuery();
                rst.next();
                int ftr = rst.getInt("ftr");
                int ptr = rst.getInt("ptr");
                String picture = rst.getString("picture");
                String tableName = ftr > 0 ? "full_time_rank" : "part_time_rank";
                int rank = ftr > 0 ? ftr : ptr;

                Statement stmDeleteRank = connection.createStatement();
                stmDeleteRank.executeUpdate("DELETE FROM " + tableName + " WHERE `rank`=" + rank);

                Statement stmShift = connection.createStatement();
                stmShift.executeUpdate("UPDATE "+ tableName +" SET `rank` = `rank` - 1 WHERE `rank` > " + rank);

                PreparedStatement stmDeleteLecturer = connection
                        .prepareStatement("DELETE FROM lecturer WHERE id = ?");
                stmDeleteLecturer.setInt(1, lecturerId);
                stmDeleteLecturer.executeUpdate();

                if (picture != null) bucket.get(picture).delete();

                connection.commit();
            } catch (Throwable t) {
                connection.rollback();
                throw t;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @GetMapping
    public List<LecturerResTO> getAllLecturers(){
        try(Connection connection = pool.getConnection()) {
            PreparedStatement selectStm = connection.prepareStatement("SELECT * FROM lecturer l INNER JOIN full_time_rank AS ftr ON l.id= ftr.lecturer_id ORDER BY `rank` ASC");
            ResultSet resultSet = selectStm.executeQuery();
            List<LecturerResTO> lecturerResTOS = new ArrayList<>();
            while (resultSet.next()){
                int lecID = resultSet.getInt("id");
                String lecName = resultSet.getString("name");
                String lecDesignation = resultSet.getString("designation");
                String lecQuli = resultSet.getString("qulifications");
                String picName = resultSet.getString("picture");
                String leclinkedin = resultSet.getString("linkedin");
                int rank = resultSet.getInt("rank");

                LecturerResTO lec = new LecturerResTO(lecID, lecName, lecDesignation, lecQuli, "full-time", picName, leclinkedin);
                lecturerResTOS.add(lec);
            }
            PreparedStatement selectStm2 = connection.prepareStatement("SELECT * FROM lecturer l INNER JOIN part_time_rank AS ptr ON l.id= ptr.lecturer_id ORDER BY `rank` ASC");
            ResultSet resultSet2 = selectStm2.executeQuery();
            while (resultSet2.next()){
                int lecID = resultSet2.getInt("id");
                String lecName = resultSet2.getString("name");
                String lecDesignation = resultSet2.getString("designation");
                String lecQuli = resultSet2.getString("qulifications");
                String picName = resultSet2.getString("picture");
                String leclinkedin = resultSet2.getString("linkedin");
                int rank = resultSet2.getInt("rank");

                LecturerResTO lec = new LecturerResTO(lecID, lecName, lecDesignation, lecQuli, "part-time", picName, leclinkedin);
                lecturerResTOS.add(lec);
            }
            return lecturerResTOS;


        }catch (SQLException e){
            throw  new RuntimeException(e);
        }



    }
}
